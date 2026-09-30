import java.util.*;
import java.util.jar.*;
import java.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Inspect the actual release archive, catching stale mod entries and test classes. */
public final class JarIdentityCheck {
    public static void main(String[] args) throws Exception {
        int mods = 0;
        try (JarFile jar = new JarFile(args[0])) {
            Attributes attrs = jar.getManifest().getMainAttributes();
            require(args[3].equals(attrs.getValue("FMLCorePlugin")), "core plugin manifest");
            require("true".equals(attrs.getValue("FMLCorePluginContainsFMLMod")), "discover regular mod in coremod jar");
            require(jar.getJarEntry(args[3].replace('.', '/') + ".class") != null, "plugin class present");
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                require(!entry.getName().contains("Regression") && !entry.getName().contains("RuntimeCheck"), "no test probes in release");
                ClassNode node = new ClassNode(Opcodes.ASM5);
                try (InputStream input = jar.getInputStream(entry)) {
                    new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
                }
                if (node.visibleAnnotations == null) continue;
                for (Object value : node.visibleAnnotations) {
                    AnnotationNode annotation = (AnnotationNode) value;
                    if (!"Lcpw/mods/fml/common/Mod;".equals(annotation.desc)) continue;
                    mods++;
                    Map<String,Object> fields = new HashMap<String,Object>();
                    for (int i = 0; i < annotation.values.size(); i += 2)
                        fields.put((String) annotation.values.get(i), annotation.values.get(i + 1));
                    require(args[1].equals(fields.get("modid")), "merged mod ID");
                    require(args[2].equals(fields.get("name")), "Chinese display name");
                    require("*".equals(fields.get("acceptableRemoteVersions")), "server addon optional");
                }
            }
            require(mods == 1, "exactly one Forge mod entry, got " + mods);
            require(jar.getJarEntry("mcmod.info") != null, "mod list metadata present");
        }
        System.out.println("PASS release identity: one mod, Chinese name, core plugin, optional server installation, no test classes");
    }
    private static void require(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }
}
