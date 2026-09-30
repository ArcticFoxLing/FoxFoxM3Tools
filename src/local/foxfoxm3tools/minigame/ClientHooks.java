package local.foxfoxm3tools.minigame;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import project.studio.manametalmod.client.GuiCookingTable;
import project.studio.manametalmod.client.MinigameCookingAccess;
import project.studio.manametalmod.network.MessageAdvancedBrewing;
import project.studio.manametalmod.network.MessageClothesTailor;
import project.studio.manametalmod.network.PacketHandlerMana;
import project.studio.manametalmod.produce.brewing.*;
import project.studio.manametalmod.produce.casting.*;
import project.studio.manametalmod.produce.cuisine.*;
import project.studio.manametalmod.produce.textile.*;

public final class ClientHooks {
    private static final int TIMEOUT = 200;
    private Session session;
    private EntityPlayer owner;
    private static final class AutoButton extends GuiButton {
        AutoButton(int x, int y, int width, int height) { super(27960, x, y, width, height, ""); }
    }
    private static final class Session {
        final GuiScreen gui;
        final TileEntity tile;
        AutoButton button;
        boolean enabled, pending, timedOut, submitted;
        int expectedPoints, waited, cookingRequest;
        int tailorTime, tailorPoints;
        boolean tailorFinished;
        int[] plan;
        int index, action, oldForge, expectedForge, oldHammer, planPosition;
        long identity;
        boolean completing;
        Session(GuiScreen gui) { this.gui = gui; this.tile = tile(gui); }
    }

    private static TileEntity tile(GuiScreen gui) {
        if (gui instanceof GuiClothesTailor) return MinigameTailorAccess.tile((GuiClothesTailor)gui);
        if (gui instanceof GuiPotionMake) return ((ContainerPotionMake)((GuiContainer)gui).field_147002_h).te;
        if (gui instanceof GuiCookingTable) return MinigameCookingAccess.tile((GuiCookingTable)gui);
        if (gui instanceof GuiCuisine) return MinigameCuisineAccess.tile((GuiCuisine)gui);
        if (gui instanceof GuiCastingAnvil) return MinigameAnvilAccess.tile((GuiCastingAnvil)gui);
        return null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unchecked")
    public void init(GuiScreenEvent.InitGuiEvent.Post event) {
        if (tile(event.gui) == null) return;
        checkOwner();
        if (session == null || session.gui != event.gui) session = new Session(event.gui);
        for (Iterator<?> it = event.buttonList.iterator(); it.hasNext();) if (it.next() instanceof AutoButton) it.remove();
        GuiScreen gui = event.gui;
        int w, h, x, y, bw = 58, bh = 18;
        if (gui instanceof GuiClothesTailor) {
            w = 170; h = 227; x = 92; y = 120; bh = 20;
            MinigameTailorAccess.layout((GuiClothesTailor)gui);
        } else if (gui instanceof GuiPotionMake) {
            w = 172; h = 254; x = 106; y = 55; bh = 14;
        } else if (gui instanceof GuiCookingTable) {
            w = 176; h = 144; x = 112; y = 7;
        } else if (gui instanceof GuiCuisine) {
            w = 172; h = 230; x = 106; y = 210; bh = 16;
        } else {
            w = 176; h = 233; x = 8; y = 55; bw = 50;
        }
        session.button = new AutoButton((gui.field_146294_l - w) / 2 + x,
                (gui.field_146295_m - h) / 2 + y, bw, bh);
        event.buttonList.add(session.button);
        label();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void click(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.button instanceof AutoButton)) return;
        // Our ID must not enter any original action handler.
        event.setCanceled(true);
        if (session == null || session.gui != event.gui || session.timedOut) return;
        session.enabled = !session.enabled;
        if (!session.enabled) session.cookingRequest = 0;
        label();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void afterClick(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (session != null && session.gui == event.gui && session.enabled
                && event.gui instanceof GuiCookingTable && event.button.field_146127_k == 0)
            session.cookingRequest = TIMEOUT;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void open(GuiOpenEvent event) {
        if (session == null || session.gui == event.gui) return;
        boolean carry = session.enabled && session.cookingRequest > 0
                && session.gui instanceof GuiCookingTable && event.gui instanceof GuiCuisine
                && session.tile == tile(event.gui);
        session = carry ? new Session(event.gui) : null;
        if (carry) session.enabled = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tick(TickEvent.ClientTickEvent event) {
        // Before GuiCuisine.updateScreen: move the bowl before vanilla collision detection.
        if (event.phase != TickEvent.Phase.START) return;
        checkOwner();
        Minecraft mc = Minecraft.func_71410_x();
        if (session == null) return;
        if (owner == null || mc.field_71441_e == null || mc.field_71462_r != session.gui
                || session.tile.func_145831_w() != mc.field_71441_e
                || session.tile.func_145837_r()) { session = null; return; }
        if (session.cookingRequest > 0) session.cookingRequest--;
        try {
            if (session.gui instanceof GuiClothesTailor) tailor();
            else if (session.gui instanceof GuiPotionMake) brewing();
            else if (session.gui instanceof GuiCastingAnvil) anvil();
            else if (session.gui instanceof GuiCuisine && session.enabled && !session.submitted) {
                GuiCuisine gui = (GuiCuisine)session.gui;
                MinigameCuisineAccess.steer(gui);
                if (MinigameCuisineAccess.canSubmit(gui)) {
                    session.submitted = true;
                    MinigameCuisineAccess.submit(gui);
                }
            }
        } catch (RuntimeException error) {
            fail("incompatible");
            error.printStackTrace();
        } catch (LinkageError error) {
            fail("incompatible");
            error.printStackTrace();
        }
        label();
    }

    private void tailor() {
        TileEntityClothesTailor te = (TileEntityClothesTailor)session.tile;
        // ManaMetal's container sends the timer and output separately from tile NBT.
        // done() may leave the client's isStart true, and late clicks are ignored by
        // the server. Never carry that final batch's score barrier into a new round.
        boolean newRound = te.isStart && te.point < session.tailorPoints
                && (session.tailorFinished || te.time < session.tailorTime);
        if (!te.isStart || newRound) {
            clearPending(); session.tailorFinished = false;
        } else if (te.func_70301_a(5) != null || (te.time == 0 && session.tailorTime > 0)) {
            clearPending(); session.tailorFinished = true;
        }
        session.tailorTime = te.time;
        session.tailorPoints = te.point;
        if (!te.isStart || session.tailorFinished) return;
        // Keep working for the entire active round, regardless of the score.
        if (!ackPoints(te.point) || !session.enabled) return;
        if (te.data == null || te.data.length != 60) { fail("incompatible"); return; }
        int[] cells = new int[8]; int count = 0;
        for (int i = 0; i < te.data.length && count < cells.length; i++)
            if (te.data[i] != 0) cells[count++] = i;
        if (count == 0) return;
        beginPoints(te.point + count);
        for (int i = 0; i < count; i++) PacketHandlerMana.INSTANCE.sendToServer(
                new MessageClothesTailor(1, te.field_145851_c, te.field_145848_d, te.field_145849_e, cells[i]));
    }

    private void brewing() {
        TileEntityPotionMake te = (TileEntityPotionMake)session.tile;
        if (!te.isStart) { clearPending(); return; }
        if (!ackPoints(te.point) || !session.enabled) return;
        if (te.cards == null || te.cards.length != 32) { fail("incompatible"); return; }
        boolean[] used = new boolean[32]; int[] pairs = new int[8]; int count = 0;
        for (int a = 0; a < 32 && count < 4; a++) {
            if (used[a] || te.cards[a] <= 0) continue;
            for (int b = a + 1; b < 32; b++) if (!used[b] && te.cards[a] == te.cards[b]) {
                pairs[count * 2] = a; pairs[count * 2 + 1] = b; count++;
                used[a] = used[b] = true; break;
            }
        }
        if (count == 0) return;
        beginPoints(te.point + count);
        for (int i = 0; i < count; i++) PacketHandlerMana.INSTANCE.sendToServer(
                new MessageAdvancedBrewing(2, te.field_145851_c, te.field_145848_d, te.field_145849_e,
                        true, pairs[i * 2], pairs[i * 2 + 1]));
    }

    private void anvil() {
        GuiCastingAnvil gui = (GuiCastingAnvil)session.gui;
        ItemStack item = MinigameAnvilAccess.item(gui), hammer = MinigameAnvilAccess.hammer(gui);
        if (!MinigameAnvilAccess.workable(item)) {
            clearPending(); session.plan = null; session.completing = false; return;
        }
        if (session.completing) return; // Let ManaMetal send/finish its own completion once.
        long identity = MinigameAnvilAccess.identity(item);
        if (session.plan != null && identity != session.identity) {
            fail("changed"); session.plan = null; return;
        }
        int forge = MinigameAnvilAccess.value(item, "forge");
        if (session.pending) {
            boolean moved = forge != session.oldForge;
            boolean toolChanged = hammer == null || hammer.func_77960_j() != session.oldHammer;
            if (forge == session.expectedForge && (moved || toolChanged)) {
                clearPending(); session.index++; session.planPosition = forge;
                if (MinigameAnvilAccess.acknowledge(gui, session.action)) { session.completing = true; return; }
            } else { waitForReply(); return; }
        }
        if (!session.enabled) return;
        if (hammer == null || hammer.func_77973_b() != CastingCore.ItemCastingHamm
                || MinigameAnvilAccess.value(item, "temperature") <= 0) return;
        if (session.plan == null) {
            MinigameAnvilAccess.prepare(gui, item);
            session.plan = AnvilSolver.solve(forge, MinigameAnvilAccess.target(gui), MinigameAnvilAccess.required(gui));
            session.index = 0; session.identity = identity; session.planPosition = forge;
        }
        if (forge != session.planPosition) { fail("changed"); return; }
        if (session.index >= session.plan.length) { fail("changed"); return; }
        int wear = 0;
        for (int i = session.index; i < session.plan.length; i++) wear += AnvilSolver.wear(session.plan[i]);
        if (MinigameAnvilAccess.value(item, "use") <= wear
                || hammer.func_77958_k() - hammer.func_77960_j() <= 3 * (session.plan.length - session.index)) {
            fail("durability"); return;
        }
        session.action = session.plan[session.index];
        session.oldForge = forge;
        session.expectedForge = AnvilSolver.next(forge, session.action);
        session.oldHammer = hammer.func_77960_j();
        session.pending = true; session.waited = 0;
        MinigameAnvilAccess.send(gui, session.action);
    }

    private boolean ackPoints(int points) {
        if (!session.pending) return true;
        if (points >= session.expectedPoints) { clearPending(); return true; }
        waitForReply(); return false;
    }
    private void beginPoints(int points) { session.pending = true; session.waited = 0; session.expectedPoints = points; }
    private void clearPending() { session.pending = false; session.waited = 0; session.timedOut = false; }
    private void waitForReply() {
        if (++session.waited >= TIMEOUT && !session.timedOut) {
            session.timedOut = true; fail("timeout");
        }
    }
    private void fail(String key) {
        if (session == null) return;
        session.enabled = false;
        if (owner != null) owner.func_145747_a(new ChatComponentText(tr("prefix") + tr(key)));
    }
    private void checkOwner() {
        EntityPlayer player = Minecraft.func_71410_x().field_71439_g;
        if (owner != player) { owner = player; session = null; }
    }
    private void label() {
        if (session != null && session.button != null) {
            session.button.field_146126_j = tr(session.timedOut ? "waiting" : session.enabled ? "on" : "off");
            session.button.field_146124_l = !session.timedOut;
        }
    }
    private static String tr(String key) { return StatCollector.func_74838_a("minigameauto." + key); }
}
