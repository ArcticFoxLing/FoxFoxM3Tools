package local.foxfoxm3tools.outputcollect;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.input.Mouse;

public final class ClientHooks {
    private World world;
    private EntityPlayer owner;
    private GuiContainer gui;
    private Container container;
    private CollectButton button;
    private boolean enabled;
    private int delay;
    private String state = "off";

    private static final class CollectButton extends GuiButton {
        CollectButton() { super(27991, 0, 0, 116, 20, ""); }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    @SuppressWarnings("unchecked")
    public void init(GuiScreenEvent.InitGuiEvent.Post event) {
        syncWorld(Minecraft.func_71410_x());
        if (!(event.gui instanceof GuiContainer)) return;
        GuiContainer next = (GuiContainer)event.gui;
        if (!OutputRegistry.supports(next.field_147002_h)) return;
        if (gui != next || container != next.field_147002_h) clearScreen();
        gui = next;
        container = next.field_147002_h;
        for (Iterator<?> it = event.buttonList.iterator(); it.hasNext();)
            if (it.next() instanceof CollectButton) it.remove();
        button = new CollectButton();
        place(event.buttonList);
        event.buttonList.add(button);
        refresh();
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void click(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.button instanceof CollectButton)) return;
        event.setCanceled(true);
        syncWorld(Minecraft.func_71410_x());
        if (event.gui != gui || event.button != button || !button.field_146124_l) return;
        enabled = !enabled;
        state = enabled ? "on" : "off";
        delay = 4;
        refresh();
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        final Minecraft mc = Minecraft.func_71410_x();
        syncWorld(mc);
        if (gui == null) return;
        if (mc.field_71462_r != gui || mc.field_71439_g == null || mc.field_71441_e == null
                || mc.field_71439_g.field_71070_bA != container) { clearScreen(); return; }
        if (!enabled || !ready(mc)) { refresh(); return; }
        if (mc.field_71439_g.field_71071_by.func_70445_o() != null
                || (Mouse.isCreated() && (Mouse.isButtonDown(0) || Mouse.isButtonDown(1)))) {
            state = "paused"; delay = 4; refresh(); return;
        }
        if (delay-- > 0) { refresh(); return; }
        delay = 4;
        final EntityPlayer player = mc.field_71439_g;
        final Container active = container;
        final GuiContainer screen = gui;
        final int window = active.field_75152_c;
        int[] outputs = OutputRegistry.slots(active, player);
        if (outputs == null) { enabled = false; state = "stopped"; refresh(); return; }
        CollectActions.Result result = CollectActions.collect(active, player, outputs, new CollectActions.Clicker() {
            public void click(int slot) {
                if (mc.field_71462_r != screen || mc.field_71439_g != player
                        || player.field_71070_bA != active || active.field_75152_c != window) return;
                mc.field_71442_b.func_78753_a(window, slot, 0, 0, player);
            }
        });
        if (result == CollectActions.Result.STOPPED) { enabled = false; state = "stopped"; }
        else state = result == CollectActions.Result.FULL ? "full" : "on";
        refresh();
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void open(GuiOpenEvent event) {
        syncWorld(Minecraft.func_71410_x());
        if (event.gui != gui) clearScreen();
    }

    private boolean ready(Minecraft mc) {
        return gui != null && mc.field_71462_r == gui && mc.field_71439_g != null
                && mc.field_71441_e != null && mc.field_71442_b != null
                && mc.field_71439_g.func_70089_S() && mc.field_71439_g.field_71070_bA == container
                && container.field_75152_c > 0;
    }

    private void refresh() {
        if (button == null) return;
        button.field_146124_l = ready(Minecraft.func_71410_x());
        button.field_146126_j = StatCollector.func_74838_a("outputcollect."
                + (button.field_146120_f < 116 ? "short." : "") + state);
    }

    private void syncWorld(Minecraft mc) {
        if (world != mc.field_71441_e || owner != mc.field_71439_g
                || (owner != null && !owner.func_70089_S())) {
            world = mc.field_71441_e;
            owner = mc.field_71439_g;
            enabled = false;
            clearScreen();
        }
    }

    private void clearScreen() {
        // Remember the session's switch, but never retain a closed container or its window ID.
        gui = null; container = null; button = null; delay = 4; state = enabled ? "on" : "off";
    }

    private void place(List<?> buttons) {
        int width = ReflectionHelper.<Integer, GuiContainer>getPrivateValue(GuiContainer.class, gui, "field_146999_f", "xSize");
        int height = ReflectionHelper.<Integer, GuiContainer>getPrivateValue(GuiContainer.class, gui, "field_147000_g", "ySize");
        int left = (gui.field_146294_l - width) / 2, top = (gui.field_146295_m - height) / 2;
        // Scan outside the container and existing buttons, including the minigame controls.
        for (int buttonWidth : new int[]{116, 64}) {
            button.field_146120_f = buttonWidth;
            for (int x : new int[]{left + width + 4, left - button.field_146120_f - 4}) {
                for (int y = Math.max(2, top); y + 20 <= gui.field_146295_m - 2; y += 22)
                    if (fits(x, y, buttons)) { setPosition(x, y); return; }
            }
            for (int y : new int[]{top + height + 4, top - 24}) {
                for (int x = Math.max(2, left); x + button.field_146120_f <= gui.field_146294_l - 2; x += 4)
                    if (fits(x, y, buttons)) { setPosition(x, y); return; }
            }
        }
        // Minecraft may allow a GUI larger than its screen. Never cover the inventory to fit.
        button.field_146125_m = false;
    }

    private boolean fits(int x, int y, List<?> buttons) {
        if (x < 0 || y < 0 || x + button.field_146120_f > gui.field_146294_l
                || y + 20 > gui.field_146295_m) return false;
        for (Object value : buttons) {
            GuiButton other = (GuiButton)value;
            if (other.field_146125_m && x < other.field_146128_h + other.field_146120_f + 2
                    && x + button.field_146120_f + 2 > other.field_146128_h
                    && y < other.field_146129_i + other.field_146121_g + 2 && y + 22 > other.field_146129_i) return false;
        }
        return true;
    }

    private void setPosition(int x, int y) { button.field_146128_h = x; button.field_146129_i = y; }
}
