package cpw.mods.fml.common.asm.transformers;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.common.discovery.ASMDataTable;
import cpw.mods.fml.common.discovery.ASMDataTable.ASMData;
import cpw.mods.fml.relauncher.FMLRelaunchLog;

import net.minecraft.launchwrapper.IClassTransformer;

public class ModAPITransformer implements IClassTransformer {

    private static final boolean logDebugInfo = Boolean.valueOf(System.getProperty("fml.debugAPITransformer", "false"));
    private ListMultimap<String, ASMData> optionals;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass)
    {
        if (optionals == null || !optionals.containsKey(name))
        {
            return basicClass;
        }
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(basicClass);
        classReader.accept(classNode, 0);

        if (logDebugInfo) FMLRelaunchLog.finest("Optional removal - found optionals for class %s - processing", name);
        for (ASMData optional : optionals.get(name))
        {
            String modId = (String) optional.getAnnotationInfo().get("modid");

            if (Loader.isModLoaded(modId))
            {
                if (logDebugInfo) FMLRelaunchLog.finest("Optional removal skipped - mod present %s", modId);
                continue;
            }
            if (logDebugInfo) FMLRelaunchLog.finest("Optional on %s triggered - mod missing %s", name, modId);

            if ("cpw.mods.fml.common.Optional$Interface".equals(optional.getAnnotationName()))
            {
                stripInterface(classNode,(String)optional.getAnnotationInfo().get("iface"));
            }
            else
            {
                stripMethod(classNode, (String)optional.getObjectName());
            }

        }
        if (logDebugInfo) FMLRelaunchLog.finest("Optional removal - class %s processed", name);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private void stripMethod(ClassNode classNode, String methodDescriptor)
    {
        for (ListIterator<MethodNode> iterator = classNode.methods.listIterator(); iterator.hasNext();)
        {
            MethodNode method = iterator.next();
            if (methodDescriptor.equals(method.name+method.desc))
            {
                iterator.remove();
                if (logDebugInfo) FMLRelaunchLog.finest("Optional removal - method %s removed", methodDescriptor);
                return;
            }
        }
        if (logDebugInfo) FMLRelaunchLog.finest("Optional removal - method %s NOT removed - not found", methodDescriptor);
    }

    private void stripInterface(ClassNode classNode, String interfaceName)
    {
        String ifaceName = interfaceName.replace('.', '/');
        boolean found = classNode.interfaces.remove(ifaceName);
        if (found && logDebugInfo) FMLRelaunchLog.finest("Optional removal - interface %s removed", interfaceName);
        if (!found && logDebugInfo) FMLRelaunchLog.finest("Optional removal - interface %s NOT removed - not found", interfaceName);
    }

    public void initTable(ASMDataTable dataTable)
    {
        optionals = ArrayListMultimap.create();
        Set<ASMData> interfaces = dataTable.getAll("cpw.mods.fml.common.Optional$Interface");
        addData(interfaces);
        Set<ASMData> methods = dataTable.getAll("cpw.mods.fml.common.Optional$Method");
        addData(methods);
    }

    private void addData(Set<ASMData> interfaces)
    {
        for (ASMData data : interfaces)
        {
            optionals.put(data.getClassName(),data);
        }
    }

}
