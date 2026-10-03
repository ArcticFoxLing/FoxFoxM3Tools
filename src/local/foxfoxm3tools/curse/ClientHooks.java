package local.foxfoxm3tools.curse;

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
import project.studio.manametalmod.client.GuiScreenBase;

public final class ClientHooks {
    private World world;
    private EntityPlayer player;
    private boolean enabled;
    private Session session;

    private static final class AutoButton extends GuiButton {
        AutoButton(int x, int y, int width) { super(27965, x, y, width, 20, ""); }
    }

    private static final class Session {
        final GuiScreenBase gui;
        CurseAccess access;
        AutoButton button;
        int steps;
        boolean broken, submitted;
        Session(GuiScreenBase gui) { this.gui = gui; }
    }

    private void syncWorld(Minecraft mc) {
        if (world != mc.field_71441_e || player != mc.field_71439_g) {
            world = mc.field_71441_e;
            player = mc.field_71439_g;
            enabled = false;
            session = null;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unchecked")
    public void init(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!CurseAccess.supports(event.gui)) return;
        syncWorld(Minecraft.func_71410_x());
        GuiScreenBase gui = (GuiScreenBase)event.gui;
        if (session == null || session.gui != gui) session = new Session(gui);
        for (Iterator<?> it = event.buttonList.iterator(); it.hasNext();)
            if (it.next() instanceof AutoButton) it.remove();
        int x = gui.guiLeft + gui.xSize + 4;
        int width = Math.min(76, gui.field_146294_l - x - 2);
        int y = gui.guiTop + (gui.ySize > 150 ? 155 : 77);
        // At Minecraft's minimum 320px GUI width there is room for a compact
        // 39px button beside the 230px panel, even for the tall matching puzzle.
        if (width < 36) {
            width = 76;
            x = Math.max(0, (gui.field_146294_l - width) / 2);
            y = gui.guiTop + gui.ySize + 2;
        }
        session.button = new AutoButton(x, Math.max(0, Math.min(y, gui.field_146295_m - 20)), width);
        event.buttonList.add(session.button);
        try {
            if (session.access == null) session.access = new CurseAccess(gui);
            refresh(session);
        } catch (ReflectiveOperationException error) { incompatible(session, error);
        } catch (RuntimeException error) { incompatible(session, error);
        } catch (LinkageError error) { incompatible(session, error); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void click(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.button instanceof AutoButton)) return;
        event.setCanceled(true);
        Session s = session;
        if (s == null || s.gui != event.gui || s.button != event.button || s.broken || s.submitted
                || !event.button.field_146124_l) return;
        enabled = !enabled;
        refresh(s);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void open(GuiOpenEvent event) {
        if (session != null && event.gui != session.gui) session = null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.func_71410_x();
        syncWorld(mc);
        Session s = session;
        if (s == null) return;
        if (mc.field_71462_r != s.gui || player == null || world == null) { session = null; return; }
        if (!enabled || s.broken || s.submitted) return;
        try {
            if (++s.steps > 100) throw new IllegalStateException("Curse puzzle did not converge");
            s.submitted = s.access.step();
            refresh(s);
        } catch (ReflectiveOperationException error) { incompatible(s, error);
        } catch (RuntimeException error) { incompatible(s, error);
        } catch (LinkageError error) { incompatible(s, error); }
    }

    private void refresh(Session s) {
        s.button.field_146124_l = !s.broken && !s.submitted;
        String key = s.broken ? "unavailable" : s.submitted ? "done" : enabled ? "on" : "off";
        if (s.button.field_146120_f < 60) key += ".short";
        s.button.field_146126_j = StatCollector.func_74838_a("curseauto." + key);
    }

    private void incompatible(Session s, Throwable error) {
        if (s.broken) return;
        s.broken = true;
        enabled = false;
        refresh(s);
        if (player != null) player.func_145747_a(new ChatComponentTranslation("curseauto.incompatible"));
        System.err.println("[ManaMetal-Curse-Auto] Stopped: incompatible curse interface.");
        error.printStackTrace();
    }
}
