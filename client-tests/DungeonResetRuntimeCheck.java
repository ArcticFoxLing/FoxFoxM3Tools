package local.dungeonresettest;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.util.Vec3;
import net.minecraft.world.*;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import project.studio.manametalmod.core.Pos;
import project.studio.manametalmod.instance_dungeon.*;
import project.studio.manametalmod.itemAndBlockCraft.ItemCraft2;

/** Original client item, portal, GUI handler and use-packet path in an isolated world. */
@Mod(modid="dungeonresettest", name="Dungeon Reset Scroll Test", version="1",
        dependencies="required-after:manametalmod;required-after:foxfoxm3tools")
public final class DungeonResetRuntimeCheck {
    private boolean done;
    private int checks;
    private Minecraft mc;
    private WorldClient world;
    private EntityClientPlayerMP player;
    private TileEntityInstanceDungeon entrance, other;
    private final List<Packet> packets = new ArrayList<Packet>();
    private final List<String> report = new ArrayList<String>();

    @Mod.EventHandler public void init(FMLInitializationEvent event) { FMLCommonHandler.instance().bus().register(this); }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) throws Exception {
        mc = Minecraft.func_71410_x();
        if (done || event.phase != TickEvent.Phase.END || !(mc.field_71462_r instanceof GuiMainMenu)) return;
        done = true;
        Path root = Paths.get(System.getProperty("dungeonreset.test.root"));
        if (!mc.field_71412_D.getCanonicalFile().equals(root.resolve("client").toFile().getCanonicalFile()))
            throw new IllegalStateException("Not the isolated test client");
        try {
            local.foxfoxvalidation.MergedIdentity.verify();
            fresh(); scrollUses(); normalOpenings(); guards();
            report.add("checks=" + checks);
            report.add("LIMIT: real original client interaction/GUI/packet code; recording transport, no live server cooldown reset performed");
            report.add("status=PASS");
        } catch (Throwable failure) {
            StringWriter trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace));
            report.add(trace.toString()); report.add("status=FAIL");
        } finally {
            mc.field_71462_r = null; mc.field_71439_g = null; mc.field_71441_e = null; mc.field_71442_b = null;
            mc.func_147108_a(new GuiMainMenu());
            Files.write(root.resolve("result.txt"), report, StandardCharsets.UTF_8); mc.func_71400_g();
        }
    }
    private void fresh() {
        NetHandlerPlayClient network = new NetHandlerPlayClient(mc, null, local.foxfoxvalidation.MergedIdentity.offlineNetwork()) {
            @Override public void func_147297_a(Packet packet) { packets.add(packet); }
        };
        world = new WorldClient(network, new WorldSettings(0, WorldSettings.GameType.SURVIVAL, false, false, WorldType.field_77138_c),
                0, EnumDifficulty.PEACEFUL, mc.field_71424_I);
        player = new EntityClientPlayerMP(mc, world, mc.func_110432_I(), network, new StatFileWriter());
        mc.field_71441_e = world; mc.field_71439_g = player; mc.field_71442_b = new PlayerControllerMP(mc, network);
        world.func_73025_a(0, 0, true);
        entrance = portal(8); other = portal(10);
        player.func_70107_b(8.5, 100, 10.5);
        reset();
    }
    private TileEntityInstanceDungeon portal(int x) {
        world.func_147465_d(x, 100, 8, InstanceDungeonCore.InstanceDungeonPortal, 0, 3);
        TileEntityInstanceDungeon tile = new TileEntityInstanceDungeon();
        world.func_147455_a(x, 100, 8, tile); return tile;
    }
    private void reset() {
        mc.func_147108_a(null); step(); packets.clear();
        Arrays.fill(player.field_71071_by.field_70462_a, null);
        player.field_71071_by.field_70461_c = 0;
        player.func_70095_a(false);
        entrance.type = InstanceDungeonType.FireDungeon;
    }
    private void held(ItemStack stack) { player.field_71071_by.field_70462_a[0] = stack; }
    private ItemStack scroll() { return new ItemStack(ItemCraft2.ItemDungeonCooddownReset); }
    private void step() { FMLCommonHandler.instance().bus().post(new TickEvent.ClientTickEvent(TickEvent.Phase.END)); }
    private PlayerInteractEvent event() {
        return new PlayerInteractEvent(player, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, 8, 100, 8, 1, world);
    }
    private void arm() { MinecraftForge.EVENT_BUS.post(event()); }
    private boolean use() {
        PlayerInteractEvent event = event();
        MinecraftForge.EVENT_BUS.post(event);
        check(!event.isCanceled() && event.useBlock != Event.Result.DENY && event.useItem != Event.Result.DENY,
                "addon never cancels or denies original scroll interaction");
        return mc.field_71442_b.func_78760_a(player, world, player.func_71045_bC(), 8, 100, 8, 1,
                Vec3.func_72443_a(8.5, 101, 8.5));
    }
    private GuiInstanceDungeonLoad screen(TileEntityInstanceDungeon tile) {
        return new GuiInstanceDungeonLoad(tile.type.ordinal(), new Pos(tile), tile);
    }
    private void scrollUses() {
        int openings = 0, types = 0;
        for (InstanceDungeonType type : InstanceDungeonType.values()) {
            if ((!BlockInstanceDungeonPortal.complete[type.ordinal()] && type != InstanceDungeonType.Custom)
                    || type == InstanceDungeonType.Mechanism) continue;
            types++;
            for (int cooldown : new int[]{-1, 0, 30}) {
                reset(); entrance.type = type; entrance.rest_time = cooldown;
                ItemStack held = scroll(); held(held);
                check(use(), "original entrance accepts use");
                check(mc.field_71462_r == null, "reset scroll suppresses original GUI 235 before it appears");
                int uses = 0;
                for (Packet packet : packets) {
                    check(!(packet instanceof C0DPacketCloseWindow), "client-only entrance needs no container-close packet");
                    if (packet instanceof C08PacketPlayerBlockPlacement) {
                        C08PacketPlayerBlockPlacement p = (C08PacketPlayerBlockPlacement) packet;
                        check(p.func_149576_c() == 8 && p.func_149571_d() == 100 && p.func_149570_e() == 8
                                && p.func_149568_f() == 1 && p.func_149574_g().func_77973_b() == ItemCraft2.ItemDungeonCooddownReset
                                && p.func_149574_g().field_77994_a == 1, "one original use packet retains scroll and target");
                        uses++;
                    }
                }
                check(uses == 1 && held.field_77994_a == 1 && entrance.rest_time == cooldown,
                        "server remains responsible for item consumption and cooldown");
                for (int t = 0; t < 25; t++) step();
                check(mc.field_71462_r == null, "loading screen cannot reopen main dungeon screen later");
                openings++;
            }
        }
        check(types > 10, "cover original entrance types including custom dungeon");
        report.add("PASS: " + openings + " scroll uses across " + types + " original entrance types and absent/zero/active cooldowns; original client item/portal/controller/GUI handler, unchanged C08 use packets, no close packets or client-side consumption/reset");
    }
    private void normalOpenings() {
        for (ItemStack item : new ItemStack[]{null, new ItemStack(Items.field_151045_i), new ItemStack(InstanceDungeonCore.ItemKeyDungeon)}) {
            reset(); held(item); player.field_71071_by.field_70462_a[9] = scroll();
            check(use() && mc.field_71462_r instanceof GuiInstanceDungeonLoad,
                    "empty hand and other items open normally even with scroll in inventory");
            for (int t = 0; t < 20; t++) mc.field_71462_r.func_73876_c();
            check(mc.field_71462_r instanceof GuiInstanceDungeon, "normal loading transitions into original dungeon selection");
        }
        reset(); held(scroll()); mc.func_147108_a(screen(entrance));
        check(mc.field_71462_r instanceof GuiInstanceDungeonLoad, "merely holding scroll never closes an unrelated/manual opening");
        reset(); held(scroll()); arm(); GuiScreen unrelated = new GuiInventoryForTest(); mc.func_147108_a(unrelated);
        check(mc.field_71462_r == unrelated, "unrelated screen stays open");
        mc.func_147108_a(null); mc.func_147108_a(screen(entrance));
        check(mc.field_71462_r instanceof GuiInstanceDungeonLoad, "unrelated opening consumes pending intent");
        report.add("PASS: empty/other hand with inventory scroll retains normal loading and selection; manually opened and unrelated screens preserved");
    }
    private void guards() {
        for (int mode = 0; mode < 3; mode++) {
            reset(); held(scroll()); PlayerInteractEvent e = event();
            if (mode == 0) e.setCanceled(true);
            if (mode == 1) e.useBlock = Event.Result.DENY;
            if (mode == 2) e.useItem = Event.Result.DENY;
            MinecraftForge.EVENT_BUS.post(e); mc.func_147108_a(screen(entrance));
            check(mc.field_71462_r instanceof GuiInstanceDungeonLoad, "canceled/denied interaction does not arm suppression");
        }
        reset(); held(scroll()); arm(); mc.func_147108_a(screen(other));
        check(mc.field_71462_r instanceof GuiInstanceDungeonLoad, "another entrance is not suppressed");
        reset(); held(scroll()); arm(); step(); mc.func_147108_a(screen(entrance));
        check(mc.field_71462_r instanceof GuiInstanceDungeonLoad, "pending intent expires at end of current tick");
        reset(); held(scroll()); player.func_70095_a(true); use();
        check(mc.field_71462_r == null, "sneaking follows original no-GUI path");
        step(); player.func_70095_a(false); held(null); use();
        check(mc.field_71462_r instanceof GuiInstanceDungeonLoad, "sneaking use cannot swallow next normal opening");
        reset(); held(scroll()); arm(); held(null); mc.func_147108_a(screen(entrance));
        check(mc.field_71462_r == null, "last scroll consumption does not invalidate the recorded use");
        reset(); held(scroll()); arm(); mc.func_147108_a(screen(entrance)); mc.func_147108_a(screen(entrance));
        check(mc.field_71462_r instanceof GuiInstanceDungeonLoad, "one interaction suppresses only one opening");
        reset(); held(scroll()); arm(); entrance.func_145843_s();
        GuiOpenEvent opening = new GuiOpenEvent(screen(entrance)); MinecraftForge.EVENT_BUS.post(opening);
        check(!opening.isCanceled(), "invalidated tile is not suppressed"); entrance.func_145829_t();
        reset(); held(scroll()); arm(); mc.field_71439_g = null;
        opening = new GuiOpenEvent(screen(entrance)); MinecraftForge.EVENT_BUS.post(opening);
        check(!opening.isCanceled(), "player departure clears pending intent"); mc.field_71439_g = player;
        reset(); held(scroll()); arm(); mc.field_71441_e = null;
        opening = new GuiOpenEvent(screen(entrance)); MinecraftForge.EVENT_BUS.post(opening);
        check(!opening.isCanceled(), "world departure clears pending intent"); mc.field_71441_e = world;
        reset(); held(scroll()); arm();
        PlayerInteractEvent air = new PlayerInteractEvent(player, PlayerInteractEvent.Action.RIGHT_CLICK_AIR, 0, 0, 0, -1, world);
        MinecraftForge.EVENT_BUS.post(air); mc.func_147108_a(screen(entrance));
        check(mc.field_71462_r instanceof GuiInstanceDungeonLoad, "new interaction replaces pending intent");
        report.add("PASS: canceled/denied interaction, different/invalid entrance, end-of-tick expiry, sneaking, last scroll, one-shot behavior, intervening interaction, player/world departure");
    }
    private void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
    private static final class GuiInventoryForTest extends GuiScreen { }
}
