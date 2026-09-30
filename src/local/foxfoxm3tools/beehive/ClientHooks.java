package local.foxfoxm3tools.beehive;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import project.studio.manametalmod.produce.beekeeping.ContainerHoneycomb;
import project.studio.manametalmod.produce.beekeeping.Guihoneycomb;

public final class ClientHooks {
    private Guihoneycomb gui;
    private ActionButton drop, refill;
    private boolean busy;

    private static final class ActionButton extends GuiButton {
        final boolean harvest;
        ActionButton(boolean harvest, int x, int y) {
            super(harvest ? 27971 : 27972, x, y, 60, 20,
                    StatCollector.func_74838_a(harvest ? "beehiveclient.drop" : "beehiveclient.refill"));
            this.harvest = harvest;
        }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    @SuppressWarnings("unchecked")
    public void init(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof Guihoneycomb)) return;
        gui = (Guihoneycomb)event.gui;
        for (Iterator<?> it = event.buttonList.iterator(); it.hasNext();)
            if (it.next() instanceof ActionButton) it.remove();
        // ManaMetal's actual beehive panel is 186 x 155 pixels.
        int left = (gui.field_146294_l - 186) / 2, top = (gui.field_146295_m - 155) / 2;
        int x = left + 190, y = top + 8;
        if (x + 60 <= gui.field_146294_l) {
            drop = new ActionButton(true, x, y);
            refill = new ActionButton(false, x, y + 24);
        } else {
            x = Math.max(0, left + 31);
            y = Math.min(gui.field_146295_m - 20, top + 159);
            drop = new ActionButton(true, x, y);
            refill = new ActionButton(false, x + 64, y);
        }
        event.buttonList.add(drop);
        event.buttonList.add(refill);
        refresh();
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void click(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.button instanceof ActionButton)) return;
        event.setCanceled(true);
        final Minecraft mc = Minecraft.func_71410_x();
        if (event.gui != gui || (event.button != drop && event.button != refill) || !ready(mc)
                || !event.button.field_146124_l) return;
        final ContainerHoneycomb container = gui.getContainer();
        final EntityPlayer player = mc.field_71439_g;
        final int window = container.field_75152_c;
        busy = true;
        try {
            HiveActions.Clicker clicks = new HiveActions.Clicker() {
                public void click(int slot, int button, int mode) {
                    // Do not send queued clicks to another screen/window.
                    if (mc.field_71462_r != gui || mc.field_71439_g != player
                            || player.field_71070_bA != container || container.field_75152_c != window)
                        throw new IllegalStateException("Beehive window changed during a click");
                    mc.field_71442_b.func_78753_a(window, slot, button, mode, player);
                }
            };
            if (((ActionButton)event.button).harvest) HiveActions.dropProducts(container, player, clicks);
            else HiveActions.refill(container, player, clicks);
        } finally {
            busy = false;
            refresh();
        }
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && gui != null) refresh();
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void open(GuiOpenEvent event) {
        if (gui != null && event.gui != gui) { gui = null; drop = null; refill = null; }
    }

    private boolean ready(Minecraft mc) {
        return !busy && gui != null && mc.field_71462_r == gui && mc.field_71441_e != null
                && mc.field_71439_g != null && mc.field_71442_b != null
                && mc.field_71439_g.field_71070_bA == gui.getContainer()
                && gui.getContainer().field_75152_c > 0 && gui.getContainer().field_75151_b.size() == 63
                && mc.field_71439_g.field_71071_by.func_70445_o() == null;
    }

    private void refresh() {
        if (drop == null || refill == null) return;
        Minecraft mc = Minecraft.func_71410_x();
        boolean ready = ready(mc);
        drop.field_146124_l = ready && HiveActions.hasProducts(gui.getContainer(), mc.field_71439_g);
        refill.field_146124_l = ready && HiveActions.canRefill(gui.getContainer(), mc.field_71439_g);
    }
}
