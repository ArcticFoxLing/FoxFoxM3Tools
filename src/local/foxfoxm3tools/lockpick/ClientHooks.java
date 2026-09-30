package local.foxfoxm3tools.lockpick;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import project.studio.manametalmod.instance_dungeon.GuiGameUnlock;
import project.studio.manametalmod.instance_dungeon.LockpickAutoAccess;

public final class ClientHooks {
    private Session session;

    private static final class AutoButton extends GuiButton {
        AutoButton(int x, int y) { super(27964, x, y, 76, 20, ""); }
    }

    private static final class Session {
        final GuiGameUnlock gui;
        final EntityPlayer player;
        final World world;
        AutoButton button;
        LockpickAutoAccess.Motion motion;
        boolean enabled, broken;
        Session(GuiGameUnlock gui, Minecraft mc) {
            this.gui = gui; player = mc.field_71439_g; world = mc.field_71441_e;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unchecked")
    public void init(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiGameUnlock)) return;
        Minecraft mc = Minecraft.func_71410_x();
        GuiGameUnlock gui = (GuiGameUnlock)event.gui;
        if (session == null || session.gui != gui || session.player != mc.field_71439_g
                || session.world != mc.field_71441_e) session = new Session(gui, mc);
        for (Iterator<?> it = event.buttonList.iterator(); it.hasNext();)
            if (it.next() instanceof AutoButton) it.remove();

        int x = gui.guiLeft + gui.xSize + 4, y = gui.guiTop + 85;
        // Small GUI scales have too little side space; place it below the panel.
        if (x + 76 > gui.field_146294_l - 4) {
            x = gui.guiLeft + gui.xSize - 76;
            y = gui.guiTop + gui.ySize + 4;
        }
        x = Math.max(0, Math.min(x, gui.field_146294_l - 76));
        y = Math.max(0, Math.min(y, gui.field_146295_m - 20));
        session.button = new AutoButton(x, y);
        event.buttonList.add(session.button);
        refresh();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void click(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.button instanceof AutoButton)) return;
        event.setCanceled(true);
        if (session == null || session.gui != event.gui || session.broken
                || session.button != event.button || !event.button.field_146124_l) return;
        session.enabled = !session.enabled;
        session.motion = null;
        refresh();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void open(GuiOpenEvent event) {
        if (session != null && event.gui != session.gui) session = null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tick(TickEvent.ClientTickEvent event) {
        if (session == null) return;
        Minecraft mc = Minecraft.func_71410_x();
        if (mc.field_71462_r != session.gui || mc.field_71439_g == null
                || mc.field_71439_g != session.player || mc.field_71441_e == null
                || mc.field_71441_e != session.world) {
            session = null;
            return;
        }
        if (session.broken) return;
        try {
            if (event.phase == TickEvent.Phase.START) {
                session.motion = session.enabled ? LockpickAutoAccess.beforeTick(session.gui) : null;
                return;
            }
            LockpickAutoAccess.Motion motion = session.motion;
            session.motion = null;
            if (LockpickAutoAccess.finished(session.gui)) session.enabled = false;
            else if (session.enabled) {
                LockpickAutoAccess.start(session.gui);
                LockpickAutoAccess.play(session.gui, motion);
            }
            refresh();
        } catch (ReflectiveOperationException error) {
            incompatible(error);
        } catch (RuntimeException error) {
            incompatible(error);
        } catch (LinkageError error) {
            incompatible(error);
        }
    }

    private void refresh() {
        try {
            if (LockpickAutoAccess.finished(session.gui)) session.enabled = false;
            session.button.field_146124_l = !session.broken && !LockpickAutoAccess.finished(session.gui);
            session.button.field_146126_j = StatCollector.func_74838_a(
                    session.broken ? "lockpickauto.unavailable" : session.enabled ? "lockpickauto.on" : "lockpickauto.off");
        } catch (ReflectiveOperationException error) {
            incompatible(error);
        } catch (RuntimeException error) {
            incompatible(error);
        } catch (LinkageError error) {
            incompatible(error);
        }
    }

    private void incompatible(Throwable error) {
        if (session == null || session.broken) return;
        session.broken = true;
        session.enabled = false;
        session.button.field_146124_l = false;
        session.button.field_146126_j = StatCollector.func_74838_a("lockpickauto.unavailable");
        if (session.player != null)
            session.player.func_145747_a(new ChatComponentTranslation("lockpickauto.incompatible"));
        System.err.println("[FoxFoxM3Tools/Lockpick] Original lockpicking interface is incompatible.");
        error.printStackTrace();
    }
}
