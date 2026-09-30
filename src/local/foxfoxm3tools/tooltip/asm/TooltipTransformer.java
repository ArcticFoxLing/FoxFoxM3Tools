package local.foxfoxm3tools.tooltip.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Adds an overflow-only early return; the original renderer remains intact. */
public final class TooltipTransformer implements IClassTransformer, Opcodes {
    public static final String TARGET = "project.studio.manametalmod.tooltip.ToolTipM3";
    public static final String HOOK = "local/foxfoxm3tools/tooltip/client/ColumnRenderer";
    public static final String DESC = "(Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;"
            + "Lnet/minecraft/client/gui/GuiScreen;Lnet/minecraft/item/ItemStack;"
            + "Lproject/studio/manametalmod/api/Quality;)V";

    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        ClassNode node = new ClassNode(ASM5);
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            if (!"drawToolTIP".equals(method.name) || !DESC.equals(method.desc)
                    || (method.access & ACC_STATIC) == 0) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode && HOOK.equals(((MethodInsnNode) insn).owner)) return bytes;
            }
            InsnList prefix = new InsnList();
            for (int i = 0; i < 7; i++) prefix.add(new VarInsnNode(i == 1 || i == 2 ? ILOAD : ALOAD, i));
            prefix.add(new MethodInsnNode(INVOKESTATIC, HOOK, "drawIfOverflow",
                    DESC.substring(0, DESC.length() - 1) + "Z", false));
            LabelNode original = new LabelNode();
            prefix.add(new JumpInsnNode(IFEQ, original));
            prefix.add(new InsnNode(RETURN));
            prefix.add(original);
            prefix.add(new FrameNode(F_NEW, 7, new Object[] {"java/util/List", INTEGER, INTEGER,
                    "net/minecraft/client/gui/FontRenderer", "net/minecraft/client/gui/GuiScreen",
                    "net/minecraft/item/ItemStack", "project/studio/manametalmod/api/Quality"},
                    0, new Object[0]));
            method.instructions.insert(prefix);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            System.out.println("[ManaMetal-Tooltip-Columns] Applied overflow columns to ToolTipM3");
            return writer.toByteArray();
        }
        System.err.println("[ManaMetal-Tooltip-Columns] Unsupported ToolTipM3 signature; original kept");
        return bytes;
    }
}
