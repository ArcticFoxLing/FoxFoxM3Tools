package local.foxfoxm3tools.skill;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import project.studio.manametalmod.MMM;
import project.studio.manametalmod.entity.nbt.ManaMetalModRoot;
import project.studio.manametalmod.event.EventGUI;
import project.studio.manametalmod.network.MessageSkill;
import project.studio.manametalmod.network.PacketHandlerMana;

/** One press casts the occupied slots of one original combination row. */
@SideOnly(Side.CLIENT)
public final class ClientHooks {
    private final KeyBinding first = new KeyBinding("key.foxfoxm3tools.skillRow1",
            Keyboard.KEY_NONE, "key.categories.foxfoxm3tools");
    private final KeyBinding second = new KeyBinding("key.foxfoxm3tools.skillRow2",
            Keyboard.KEY_NONE, "key.categories.foxfoxm3tools");
    private boolean firstHeld;
    private boolean secondHeld;

    public ClientHooks() {
        ClientRegistry.registerKeyBinding(first);
        ClientRegistry.registerKeyBinding(second);
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // Consume even in menus so a discarded press cannot cast after closing a GUI.
        boolean castFirst = consume(first) && !firstHeld;
        boolean castSecond = consume(second) && !secondHeld;
        firstHeld = first.func_151470_d();
        secondHeld = second.func_151470_d();
        Minecraft mc = Minecraft.func_71410_x();
        if (mc.field_71439_g == null || mc.field_71441_e == null || mc.field_71462_r != null
                || !mc.field_71415_G || mc.func_147113_T() || !mc.field_71439_g.func_70089_S()
                || !MMM.canUpdate(mc.field_71439_g) || EventGUI.disableKeyboard > 0) return;
        if (!castFirst && !castSecond) return;
        ManaMetalModRoot root = MMM.getEntityNBT(mc.field_71439_g);
        if (root == null || root.carrer == null || root.carrer.isDead()) return;
        if (castFirst) cast(root.carrer.spellKey_2);
        if (castSecond) cast(root.carrer.spellKey_3);
    }

    private static boolean consume(KeyBinding key) {
        boolean pressed = false;
        while (key.func_151468_f()) pressed = true;
        return key.func_151463_i() != Keyboard.KEY_NONE && pressed;
    }

    private static void cast(int[] slots) {
        if (slots == null) return;
        for (int slot = 0; slot < Math.min(7, slots.length); slot++) {
            int skill = slots[slot];
            // -1 is empty; small IDs are skill-upgrade commands, never cast them.
            if (skill < 100) continue;
            boolean duplicate = false;
            for (int previous = 0; previous < slot; previous++) {
                if (slots[previous] == skill) { duplicate = true; break; }
            }
            if (!duplicate) PacketHandlerMana.INSTANCE.sendToServer(new MessageSkill(skill));
        }
    }
}
