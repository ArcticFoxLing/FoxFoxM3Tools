package local.foxfoxm3tools.bosssummon;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import project.studio.manametalmod.ManaMetalMod;
import project.studio.manametalmod.bosssummon.*;

public final class ClientHooks {
    private int ticks;
    private Pending pending;
    private GuiTileEntityBossSummon gui;
    private QuickButton save, clear;

    private static final class QuickButton extends GuiButton {
        QuickButton(int id, int x, int y, int width, String key) {
            super(id, x, y, width, 20, StatCollector.func_74838_a(key));
        }
    }
    private static final class Pending {
        final EntityClientPlayerMP player;
        final TileEntityBossSpawn tile;
        final int boss, started;
        GuiTileEntityBossSummon screen;
        Pending(EntityClientPlayerMP player, TileEntityBossSpawn tile, int boss, int tick) {
            this.player = player; this.tile = tile; this.boss = boss; this.started = tick;
        }
    }

    private static boolean holdsCrystal(EntityClientPlayerMP player) {
        ItemStack held = player == null ? null : player.func_71045_bC();
        return held != null && held.field_77994_a > 0
                && held.func_77973_b() == Item.func_150898_a(ManaMetalMod.ManaCrystal);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void interact(PlayerInteractEvent event) {
        Minecraft mc = Minecraft.func_71410_x();
        // Ignore integrated-server events and interactions made by other players.
        if (event.entityPlayer != mc.field_71439_g || event.world != mc.field_71441_e) return;
        pending = null;
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || event.isCanceled()
                || event.useBlock == Event.Result.DENY || mc.field_71462_r != null
                || !holdsCrystal(mc.field_71439_g) || BossSettings.selected() < 0
                || event.world.func_147439_a(event.x, event.y, event.z) != BossSummonCore.BlockTileEntityBossSpawns) return;
        net.minecraft.tileentity.TileEntity tile = event.world.func_147438_o(event.x, event.y, event.z);
        if (tile instanceof TileEntityBossSpawn)
            pending = new Pending(mc.field_71439_g, (TileEntityBossSpawn) tile, BossSettings.selected(), ticks);
        // Allow the original block interaction to open the real GUI normally.
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void open(GuiOpenEvent event) {
        if (event.gui != gui) { gui = null; save = null; clear = null; }
        if (pending == null) return;
        if (event.isCanceled() || !(event.gui instanceof GuiTileEntityBossSummon)
                || BossQuickSummonAccess.tile((GuiTileEntityBossSummon) event.gui) != pending.tile) {
            pending = null;
        } else pending.screen = (GuiTileEntityBossSummon) event.gui;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unchecked")
    public void init(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiTileEntityBossSummon)) return;
        gui = (GuiTileEntityBossSummon) event.gui;
        for (Iterator<?> it = event.buttonList.iterator(); it.hasNext();)
            if (it.next() instanceof QuickButton) it.remove();
        int width = Math.min(241, gui.field_146294_l - 8);
        int x = (gui.field_146294_l - width) / 2;
        int y = gui.guiTop + gui.ySize + 4;
        int half = (width - 5) / 2;
        save = new QuickButton(27981, x, y, half, "bosssummonquick.save");
        clear = new QuickButton(27982, x + half + 5, y, half, "bosssummonquick.clear");
        event.buttonList.add(save); event.buttonList.add(clear);
        refresh();
    }

    private static boolean unlocked(TileEntityBossSpawn tile, int id) {
        return id >= 0 && id < BossType.values().length && tile != null
                && tile.spawnData != null && id < tile.spawnData.length && tile.spawnData[id];
    }
    private void refresh() {
        if (gui == null || save == null) return;
        save.field_146124_l = unlocked(BossQuickSummonAccess.tile(gui), BossQuickSummonAccess.selected(gui));
        clear.field_146124_l = BossSettings.selected() >= 0;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void click(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.button instanceof QuickButton)) return;
        event.setCanceled(true);
        if (event.gui != gui || Minecraft.func_71410_x().field_71462_r != gui) return;
        refresh();
        if (!event.button.field_146124_l) return;
        if (event.button == save) BossSettings.save(BossQuickSummonAccess.selected(gui));
        else if (event.button == clear) BossSettings.save(-1);
        pending = null;
        refresh();
    }

    @SubscribeEvent public void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.gui != gui || gui == null) return;
        refresh();
        int id = BossSettings.selected();
        String boss = id < 0 ? StatCollector.func_74838_a("bosssummonquick.none")
                : StatCollector.func_74838_a("entity.manametalmod." + BlockTileEntityBossSpawn.names[id] + ".name");
        line(StatCollector.func_74837_a("bosssummonquick.current", boss), gui.guiTop - 16, 0xFFE080);
        line(StatCollector.func_74838_a("bosssummonquick.hint"), save.field_146129_i + 24, 0xDDDDDD);
    }
    private void line(String text, int y, int color) {
        Minecraft mc = Minecraft.func_71410_x();
        String visible = mc.field_71466_p.func_78269_a(text, gui.field_146294_l - 8);
        mc.field_71466_p.func_78276_b(visible, (gui.field_146294_l - mc.field_71466_p.func_78256_a(visible)) / 2, y, color);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        refresh();
        if (pending == null) return;
        Minecraft mc = Minecraft.func_71410_x();
        Pending request = pending;
        TileEntityBossSpawn tile = request.tile;
        if (mc.field_71439_g != request.player || mc.field_71441_e == null
                || mc.field_71441_e != tile.func_145831_w() || ticks - request.started >= 40
                || !holdsCrystal(request.player) || BossSettings.selected() != request.boss
                || tile.func_145837_r()
                || mc.field_71441_e.func_147438_o(tile.field_145851_c, tile.field_145848_d, tile.field_145849_e) != tile
                || request.player.func_70092_e(tile.field_145851_c + .5, tile.field_145848_d + .5, tile.field_145849_e + .5) > 64) {
            pending = null; return;
        }
        if (request.screen == null && mc.field_71462_r == null) return;
        pending = null; // One request for this interaction, before invoking vanilla callbacks.
        if (request.screen == null || mc.field_71462_r != request.screen || gui != request.screen) return;
        if (!unlocked(tile, request.boss)) {
            request.player.func_145747_a(new ChatComponentTranslation("bosssummonquick.locked"));
            return;
        }
        if (tile.isStart) {
            request.player.func_145747_a(new ChatComponentTranslation("MMM.info.bosssumom.fail.3"));
            return;
        }
        BossQuickSummonAccess.summon(request.screen, request.boss);
    }
}
