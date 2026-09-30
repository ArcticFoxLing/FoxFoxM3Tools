package local.foxfoxm3tools.reward;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import project.studio.manametalmod.mob.EntityItemHolyDevice;

/** A holy vessel arrives as a vanilla chest; associate it with the clicked entity. */
public final class HolyDeviceGuiHooks {
    private int ticks;
    private Pending pending;

    private static final class Pending {
        final EntityClientPlayerMP player;
        final EntityItemHolyDevice entity;
        final int started;
        GuiScreen screen;

        Pending(EntityClientPlayerMP player, EntityItemHolyDevice entity, int started) {
            this.player = player;
            this.entity = entity;
            this.started = started;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void interact(EntityInteractEvent event) {
        Minecraft mc = Minecraft.func_71410_x();
        if (event.entityPlayer != mc.field_71439_g) return;
        pending = null;
        if (!event.isCanceled() && mc.field_71441_e != null && mc.field_71462_r == null
                && event.target instanceof EntityItemHolyDevice
                && event.target.field_70170_p == mc.field_71441_e && !event.target.field_70128_L) {
            pending = new Pending(mc.field_71439_g, (EntityItemHolyDevice) event.target, ticks);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void interactBlock(PlayerInteractEvent event) {
        if (event.entityPlayer == Minecraft.func_71410_x().field_71439_g
                && event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) pending = null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void open(GuiOpenEvent event) {
        if (pending == null) return;
        if (event.isCanceled() || !matches(event.gui)) pending = null;
        else pending.screen = event.gui;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ticks++;
            close();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void render(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) close();
    }

    private boolean matches(GuiScreen screen) {
        if (screen == null || screen.getClass() != GuiChest.class) return false;
        Container container = ((GuiChest) screen).field_147002_h;
        if (container.getClass() != ContainerChest.class) return false;
        IInventory inventory = ((ContainerChest) container).func_85151_d();
        if (inventory.func_70302_i_() != 54 || !inventory.func_145818_k_()) return false;
        String title = inventory.func_145825_b();
        // The server sends a literal title in its own language, which can differ
        // from the client's. These are the shipped ManaMetal 8.0.7 translations.
        return pending.entity.func_145825_b().equals(title)
                || "圣器".equals(title) || "聖器".equals(title)
                || "Holy Vessel".equals(title) || "Artefacto sagrado".equals(title);
    }

    private void close() {
        if (pending == null) return;
        Minecraft mc = Minecraft.func_71410_x();
        if (mc.field_71439_g != pending.player || mc.field_71441_e == null
                || mc.field_71441_e != pending.entity.field_70170_p
                || pending.entity.field_70128_L || ticks - pending.started >= 100) {
            pending = null;
            return;
        }
        if (mc.field_71462_r == null && pending.screen == null) return; // Await server reply.
        if (mc.field_71462_r != pending.screen || !matches(mc.field_71462_r)) {
            pending = null;
            return;
        }
        Container container = ((GuiChest) pending.screen).field_147002_h;
        if (pending.player.field_71070_bA != container || container.field_75152_c <= 0) return;
        // Wait until the real OpenWindow handler has installed the window ID.
        // Normal close -> ContainerChest -> EntityItemHolyDevice.closeInventory
        // releases the remaining loot on the server and removes the vessel.
        EntityClientPlayerMP player = pending.player;
        pending = null;
        player.func_71053_j();
    }
}
