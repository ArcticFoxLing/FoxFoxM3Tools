package local.foxfoxm3tools.reward;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;

/** Hooks only the reward-box GUI; every other ManaMetal GUI is untouched. */
public final class ClientGuiHooks {
    private static final String REWARD_BOX_GUI =
            "project.studio.manametalmod.client.GuiOpenBox2";

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) closeRewardScreen();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) closeRewardScreen();
    }

    private void closeRewardScreen() {
        Minecraft minecraft = Minecraft.func_71410_x();
        EntityClientPlayerMP player = minecraft.field_71439_g;
        GuiScreen screen = minecraft.field_71462_r;
        if (player == null || minecraft.field_71441_e == null
                || !isRewardBoxGui(screen) || !(screen instanceof GuiContainer)) return;

        Container container = ((GuiContainer) screen).field_147002_h;
        // FML finishes assigning openContainer and its windowId AFTER displaying
        // the GUI. Closing inside GuiOpenEvent/initGui would close the previous
        // container (often window 0) and leave FML with a stale container.
        // Tick/render callbacks run after that network handler returns.
        if (player.field_71070_bA != container || container.field_75152_c <= 0
                || !"project.studio.manametalmod.inventory.ContainerOpenBox"
                        .equals(container.getClass().getName())) return;

        // The normal Esc path sends exactly one C0DPacketCloseWindow and resets
        // openContainer. ManaMetal's original server code releases the rewards.
        // Do not require a held box: opening the last box may have consumed it.
        player.func_71053_j();
    }

    static boolean isRewardBoxGui(GuiScreen gui) {
        return gui != null && REWARD_BOX_GUI.equals(gui.getClass().getName());
    }
}
