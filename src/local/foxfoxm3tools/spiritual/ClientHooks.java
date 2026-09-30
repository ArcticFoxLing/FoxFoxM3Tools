package local.foxfoxm3tools.spiritual;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import project.studio.manametalmod.client.GuiSpiritualPower;
import project.studio.manametalmod.client.ManaButton;
import project.studio.manametalmod.client.SpiritualAutoResetAccess;
import project.studio.manametalmod.core.SpiritualPower;
import project.studio.manametalmod.entity.nbt.ManaMetalModRoot;

public final class ClientHooks {
    private static final int STAGES = 8;
    private static final int RESPONSE_TIMEOUT = 200;
    private GuiSpiritualPower screen;
    private EntityPlayer owner;
    private AutoButton[] buttons = new AutoButton[STAGES];
    private TargetButton[] targets = new TargetButton[6];
    // Shared by all eight stages and retained when the screen is reopened.
    private int targetQuality = 6;
    private int active = -1;
    private int attempts;
    private Pending pending;
    private Pending manualCandidate;

    private static final class AutoButton extends ManaButton {
        final int stage;
        AutoButton(int stage, GuiButton original) {
            super(27840 + stage, original.field_146128_h + original.field_146120_f + 2,
                    original.field_146129_i, 34, original.field_146121_g, tr("auto"), 10, true);
            this.stage = stage;
        }
    }

    private static final class TargetButton extends GuiButton {
        final int quality;
        boolean selected;

        TargetButton(int quality, GuiSpiritualPower gui) {
            super(27850 + quality, gui.guiLeft + 9 + (quality - 1) * 40,
                    gui.guiTop + 18, 38, 11, "");
            this.quality = quality;
        }

        @Override public void func_146112_a(Minecraft mc, int mouseX, int mouseY) {
            if (!field_146125_m) return;
            boolean hover = mouseX >= field_146128_h && mouseX < field_146128_h + field_146120_f
                    && mouseY >= field_146129_i && mouseY < field_146129_i + field_146121_g;
            func_73734_a(field_146128_h, field_146129_i, field_146128_h + field_146120_f,
                    field_146129_i + field_146121_g, selected ? 0xFFD7B65D : hover ? 0xFFCEC5A4 : 0xFF74736A);
            func_73734_a(field_146128_h + 1, field_146129_i + 1, field_146128_h + field_146120_f - 1,
                    field_146129_i + field_146121_g - 1, selected ? 0xFF584829 : 0xFF302F29);
            func_73732_a(mc.field_71466_p, field_146126_j, field_146128_h + field_146120_f / 2,
                    field_146129_i + 2, selected ? 0xFFFF88 : 0xE0E0E0);
        }
    }

    private static final class Pending {
        final int stage, points, cost, stones, day;
        int ticks;
        boolean timedOut;
        Pending(int stage, ManaMetalModRoot root) {
            this.stage = stage;
            points = root.carrer.SpiritualPowerCount;
            cost = SpiritualPower.needPower(stage, root);
            stones = SpiritualAutoResetAccess.stones();
            day = SpiritualAutoResetAccess.day(root);
        }
        boolean acknowledged(ManaMetalModRoot root) {
            // Quality may repeat exactly. Require authoritative resource changes,
            // including inventory sync, instead of treating an animation as a reply.
            return root.carrer.SpiritualPower[stage] > 0
                    && root.carrer.SpiritualPowerCount <= points - cost
                    && SpiritualAutoResetAccess.day(root) >= Math.min(100, day + 1)
                    && (stones == 0 || SpiritualAutoResetAccess.stones() < stones);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unchecked")
    public void initGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiSpiritualPower)) return;
        GuiSpiritualPower gui = (GuiSpiritualPower) event.gui;
        if (screen != gui) active = -1;
        screen = gui;
        checkOwner();
        // initGui also runs on resize; never duplicate buttons or restart a task.
        for (Iterator<?> it = event.buttonList.iterator(); it.hasNext();) {
            Object button = it.next();
            if (button instanceof AutoButton || button instanceof TargetButton) it.remove();
        }
        for (int stage = 0; stage < STAGES; stage++) {
            buttons[stage] = new AutoButton(stage, SpiritualAutoResetAccess.resetButton(gui, stage));
            event.buttonList.add(buttons[stage]);
        }
        for (int i = 0; i < targets.length; i++) {
            targets[i] = new TargetButton(i + 1, gui);
            event.buttonList.add(targets[i]);
        }
        updateButtons();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void click(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.gui instanceof GuiSpiritualPower)) return;
        GuiSpiritualPower gui = (GuiSpiritualPower) event.gui;
        if (event.button instanceof TargetButton) {
            // Selector IDs must never reach ManaMetal's stage-index handler.
            event.setCanceled(true);
            if (screen != gui) return;
            targetQuality = ((TargetButton) event.button).quality;
            // An in-flight request must still be acknowledged before deciding
            // whether its result meets the newly selected target.
            ManaMetalModRoot root = SpiritualAutoResetAccess.root();
            if (active >= 0 && pending == null && root != null
                    && root.carrer.SpiritualPower[active] == targetQuality) stop("success");
            updateButtons();
            return;
        }
        if (event.button instanceof AutoButton) {
            // ManaMetal treats ANY button ID as a stage index. Always consume ours.
            event.setCanceled(true);
            if (screen != gui) return;
            int stage = ((AutoButton) event.button).stage;
            if (active == stage) { stop("cancelled"); updateButtons(); return; }
            if (active >= 0 || pending != null || SpiritualAutoResetAccess.busy(gui)) return;
            ManaMetalModRoot root = SpiritualAutoResetAccess.root();
            String reason = unavailable(stage, root);
            if (reason != null) { message(reason, stage); return; }
            active = stage;
            attempts = 0;
            updateButtons();
            return;
        }
        // Also track manual requests so toggling auto after a slow manual reset
        // cannot send a second request before the first result arrives.
        int stage = event.button.field_146127_k;
        if (stage < 0 || stage >= STAGES
                || event.button != SpiritualAutoResetAccess.resetButton(gui, stage)) return;
        if (active >= 0 || pending != null) { event.setCanceled(true); return; }
        manualCandidate = null;
        ManaMetalModRoot root = SpiritualAutoResetAccess.root();
        if (root != null && !SpiritualAutoResetAccess.busy(gui)
                && root.carrer.SpiritualPowerCount >= SpiritualPower.needPower(stage, root)) {
            manualCandidate = new Pending(stage, root);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void afterClick(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (event.gui == screen && manualCandidate != null) {
            if (SpiritualAutoResetAccess.busy(screen)) pending = manualCandidate;
            manualCandidate = null;
            updateButtons();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void open(GuiOpenEvent event) {
        if (event.gui != screen && screen != null) {
            active = -1;
            screen = null;
            // Retain the in-flight barrier across closing/reopening the screen.
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.func_71410_x();
        checkOwner();
        if (owner == null || mc.field_71441_e == null) { active = -1; pending = null; return; }
        if (screen != mc.field_71462_r) { active = -1; screen = null; }
        if (screen == null && pending == null) return;
        ManaMetalModRoot root = SpiritualAutoResetAccess.root();
        if (root == null) { stop("missing_data"); return; }

        if (active >= 0 && (SpiritualAutoResetAccess.locked(screen, active)
                || root.carrer.getLv() < SpiritualPower.getNeedLV(active))) stop("locked");
        if (pending != null) {
            pending.ticks++;
            if (pending.acknowledged(root)) {
                pending = null;
                if (active >= 0 && root.carrer.SpiritualPower[active] == targetQuality) stop("success");
            } else if (pending.ticks >= RESPONSE_TIMEOUT && !pending.timedOut) {
                pending.timedOut = true;
                if (active >= 0) stop("timeout");
                // An uncertain request is never retried, including after reopening.
            }
        }
        if (screen == null) return;
        if (pending == null) SpiritualAutoResetAccess.refresh(screen, root);
        if (active >= 0 && pending == null && !SpiritualAutoResetAccess.busy(screen)) {
            String reason = unavailable(active, root);
            if (reason != null) stop(reason);
            else {
                Pending request = new Pending(active, root);
                // Temporarily restore this original button before invoking its action.
                SpiritualAutoResetAccess.resetButton(screen, active).field_146124_l = true;
                if (SpiritualAutoResetAccess.reset(screen, active)) { pending = request; attempts++; }
                else stop("missing_data");
            }
        }
        updateButtons();
    }

    private void checkOwner() {
        EntityPlayer player = Minecraft.func_71410_x().field_71439_g;
        if (owner != player) {
            active = -1;
            pending = null;
            manualCandidate = null;
            owner = player;
        }
    }

    private String unavailable(int stage, ManaMetalModRoot root) {
        if (root == null || root.carrer.SpiritualPower.length < STAGES) return "missing_data";
        if (root.carrer.SpiritualPower[stage] == targetQuality) return "success";
        if (SpiritualAutoResetAccess.locked(screen, stage)) return "locked";
        if (root.carrer.getLv() < SpiritualPower.getNeedLV(stage)) return "level";
        if (targetQuality == 6 && SpiritualAutoResetAccess.stones() <= 0) return "stones";
        if (root.carrer.SpiritualPowerCount < SpiritualPower.needPower(stage, root)) return "points";
        return null;
    }

    private void updateButtons() {
        if (screen == null || owner == null) return;
        ManaMetalModRoot root = SpiritualAutoResetAccess.root();
        if (root == null) return;
        for (TargetButton target : targets) {
            if (target == null) continue;
            target.selected = target.quality == targetQuality;
            String label = tr("quality_short." + target.quality);
            target.field_146126_j = target.selected ? "[" + label + "]" : label;
        }
        boolean busy = SpiritualAutoResetAccess.busy(screen);
        for (int stage = 0; stage < STAGES; stage++) {
            AutoButton button = buttons[stage];
            if (button == null) continue;
            boolean reached = root.carrer.SpiritualPower[stage] == targetQuality;
            button.field_146126_j = tr(active == stage ? "stop" : reached ? "reached" : "auto");
            button.field_146124_l = active == stage || (active == -1 && pending == null
                    && !busy && unavailable(stage, root) == null);
            SpiritualAutoResetAccess.resetButton(screen, stage).field_146124_l = active < 0
                    && pending == null && !SpiritualAutoResetAccess.locked(screen, stage)
                    && root.carrer.getLv() >= SpiritualPower.getNeedLV(stage);
        }
    }

    @SubscribeEvent
    public void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.gui != screen || owner == null) return;
        ManaMetalModRoot root = SpiritualAutoResetAccess.root();
        if (root == null) return;
        for (TargetButton target : targets) {
            if (target == null) continue;
            if (screen.DrawTooltipScreenBase(event.mouseX, event.mouseY,
                    target.field_146128_h - screen.guiLeft, target.field_146129_i - screen.guiTop,
                    target.field_146120_f, target.field_146121_g,
                    tr("target_title", tr("quality." + target.quality)),
                    tr("target_hint"), tr("target_exact"))) return;
        }
        for (AutoButton button : buttons) {
            if (button == null) continue;
            String reason = unavailable(button.stage, root);
            String detail = active == button.stage ? tr("running", attempts, qualityName())
                    : pending != null ? tr(pending.timedOut ? "waiting_timeout" : "waiting")
                    : active >= 0 ? tr("other_stage") : reason == null ? tr("hint", qualityName()) : tr(reason, qualityName());
            if (screen.DrawTooltipScreenBase(event.mouseX, event.mouseY,
                    button.field_146128_h - screen.guiLeft, button.field_146129_i - screen.guiTop,
                    button.field_146120_f, button.field_146121_g,
                    tr("title", button.stage + 1, qualityName()), detail,
                    tr(targetQuality == 6 ? "cost_hint" : "cost_hint_lower"))) break;
        }
    }

    private void stop(String reason) {
        int stage = active;
        active = -1;
        if (stage >= 0) message(reason, stage);
    }

    private void message(String reason, int stage) {
        if (owner != null) owner.func_145747_a(new ChatComponentText(
                tr("prefix", stage + 1) + tr(reason, qualityName()) + tr("attempts", attempts)));
    }

    private String qualityName() { return tr("quality." + targetQuality); }

    private static String tr(String key, Object... args) {
        return StatCollector.func_74837_a("spiritualautoreset." + key, args);
    }
}
