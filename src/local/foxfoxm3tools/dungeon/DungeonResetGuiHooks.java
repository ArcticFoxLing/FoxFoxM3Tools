package local.foxfoxm3tools.dungeon;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import project.studio.manametalmod.config.M3Config;
import project.studio.manametalmod.instance_dungeon.DungeonResetGuiAccess;
import project.studio.manametalmod.instance_dungeon.GuiInstanceDungeonLoad;
import project.studio.manametalmod.instance_dungeon.InstanceDungeonCore;
import project.studio.manametalmod.instance_dungeon.TileEntityInstanceDungeon;
import project.studio.manametalmod.itemAndBlockCraft.ItemCraft2;

/** Hide the local entrance screen while leaving the original scroll-use packet intact. */
public final class DungeonResetGuiHooks {
    private EntityPlayer player;
    private TileEntityInstanceDungeon entrance;

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void interact(PlayerInteractEvent event) {
        Minecraft mc = Minecraft.func_71410_x();
        if (event.entityPlayer != mc.field_71439_g || event.world != mc.field_71441_e) return;
        clear();
        if (event.isCanceled() || event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
                || event.useBlock == Event.Result.DENY || event.useItem == Event.Result.DENY
                || mc.field_71462_r != null || event.world == null
                || event.world.field_73011_w.field_76574_g == M3Config.WorldInstanceDungeonID) return;
        ItemStack held = event.entityPlayer.func_71045_bC();
        if (held == null || held.field_77994_a <= 0 || held.func_77973_b() != ItemCraft2.ItemDungeonCooddownReset
                || event.world.func_147439_a(event.x, event.y, event.z) != InstanceDungeonCore.InstanceDungeonPortal) return;
        TileEntity tile = event.world.func_147438_o(event.x, event.y, event.z);
        if (tile instanceof TileEntityInstanceDungeon) {
            player = event.entityPlayer;
            entrance = (TileEntityInstanceDungeon) tile;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void open(GuiOpenEvent event) {
        TileEntityInstanceDungeon tile = entrance;
        EntityPlayer owner = player;
        clear();
        if (tile == null || event.isCanceled() || !(event.gui instanceof GuiInstanceDungeonLoad)) return;
        Minecraft mc = Minecraft.func_71410_x();
        if (mc.field_71439_g == owner && mc.field_71441_e != null && mc.field_71462_r == null
                && tile.func_145831_w() == mc.field_71441_e && !tile.func_145837_r()
                && mc.field_71441_e.func_147438_o(tile.field_145851_c, tile.field_145848_d, tile.field_145849_e) == tile
                && DungeonResetGuiAccess.tile((GuiInstanceDungeonLoad) event.gui) == tile) {
            // GUI 235 is entirely client-side and opens during block activation.
            // Cancel only this GUI, never the interaction or the C08 use request.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tick(TickEvent.ClientTickEvent event) {
        // There is no server OpenWindow reply to await; never retain this intent
        // for a later manual opening if the block declined to open a screen.
        if (event.phase == TickEvent.Phase.END) clear();
    }

    private void clear() { player = null; entrance = null; }
}
