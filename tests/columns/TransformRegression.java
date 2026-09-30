import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import local.foxfoxm3tools.tooltip.asm.TooltipTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class TransformRegression implements Opcodes {
    public static boolean handled;
    public static int hooks, originals;
    public static boolean hook() { hooks++; return handled; }
    public static void original() { originals++; }

    public static void main(String[] args) throws Exception {
        TooltipTransformer transformer = new TooltipTransformer();
        byte[] raw;
        try (ZipFile jar = new ZipFile(args[0]); InputStream input = jar.getInputStream(jar.getEntry(TooltipTransformer.TARGET.replace('.', '/') + ".class"))) {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int read;
            while ((read = input.read(buffer)) != -1) data.write(buffer, 0, read);
            raw = data.toByteArray();
        }
        byte[] patched = transformer.transform(TooltipTransformer.TARGET, TooltipTransformer.TARGET, raw);
        require(!Arrays.equals(raw, patched), "Actual ManaMetal renderer was patched");
        require(transformer.transform(TooltipTransformer.TARGET, TooltipTransformer.TARGET, patched) == patched, "Idempotent transformer");
        require(transformer.transform("Other", "Other", raw) == raw, "Other classes untouched");
        require(transformer.transform("Other", "Other", null) == null, "Null bytes preserved");
        ClassNode before = read(raw), after = read(patched);
        require(before.methods.size() == after.methods.size(), "No methods removed");
        for (int i = 0; i < before.methods.size(); i++) {
            MethodNode a = (MethodNode) before.methods.get(i), b = (MethodNode) after.methods.get(i);
            List<Integer> originalOpcodes = opcodes(a), finalOpcodes = opcodes(b);
            if (a.name.equals("drawToolTIP") && a.desc.equals(TooltipTransformer.DESC)) {
                require(finalOpcodes.subList(10, finalOpcodes.size()).equals(originalOpcodes), "Entire original renderer retained");
            } else require(originalOpcodes.equals(finalOpcodes), "Unrelated method retained: " + a.name);
        }
        Map<String, byte[]> definitions = new HashMap<String, byte[]>();
        for (String name : new String[] {"net.minecraft.client.gui.FontRenderer", "net.minecraft.client.gui.GuiScreen",
                "net.minecraft.item.ItemStack", "project.studio.manametalmod.api.Quality"}) definitions.put(name, empty(name));
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC, TooltipTransformer.TARGET.replace('.', '/'), null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "drawToolTIP", TooltipTransformer.DESC, null, null);
        method.visitCode(); method.visitMethodInsn(INVOKESTATIC, "TransformRegression", "original", "()V", false);
        method.visitInsn(RETURN); method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd();
        definitions.put(TooltipTransformer.TARGET, transformer.transform(TooltipTransformer.TARGET, TooltipTransformer.TARGET, writer.toByteArray()));
        writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC, TooltipTransformer.HOOK, null, "java/lang/Object", null);
        method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "drawIfOverflow",
                TooltipTransformer.DESC.substring(0, TooltipTransformer.DESC.length() - 1) + "Z", null, null);
        method.visitCode(); method.visitMethodInsn(INVOKESTATIC, "TransformRegression", "hook", "()Z", false);
        method.visitInsn(IRETURN); method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd();
        definitions.put(TooltipTransformer.HOOK.replace('/', '.'), writer.toByteArray());
        final Map<String, byte[]> classes = definitions;
        ClassLoader loader = new ClassLoader(TransformRegression.class.getClassLoader()) {
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!classes.containsKey(name)) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) { byte[] bytes = classes.get(name); type = defineClass(name, bytes, 0, bytes.length); }
                if (resolve) resolveClass(type);
                return type;
            }
        };
        Method render = loader.loadClass(TooltipTransformer.TARGET).getDeclaredMethods()[0];
        handled = false; render.invoke(null, Arrays.asList("short"), 1, 2, null, null, null, null);
        require(hooks == 1 && originals == 1, "Fitting tooltips use original renderer");
        handled = true; render.invoke(null, Arrays.asList("long"), 1, 2, null, null, null, null);
        require(hooks == 2 && originals == 1, "Overflow does not also draw the original");
        System.out.println("PASS transformer: actual 8.0.7 method, original instructions retained, idempotence, JVM-verified hook branches");
    }
    private static byte[] empty(String name) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V1_8, ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null); writer.visitEnd();
        return writer.toByteArray();
    }
    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode(ASM5); new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES); return node;
    }
    private static List<Integer> opcodes(MethodNode method) {
        List<Integer> codes = new ArrayList<Integer>();
        for (AbstractInsnNode insn : method.instructions.toArray()) if (insn.getOpcode() >= 0) codes.add(insn.getOpcode());
        return codes;
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
