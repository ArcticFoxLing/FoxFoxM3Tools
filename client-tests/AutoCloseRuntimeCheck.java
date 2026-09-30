package local.rewardautoclosetest;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.*;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.Unpooled;
import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.*;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.*;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.S2DPacketOpenWindow;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import local.foxfoxm3tools.reward.ClientGuiHooks;
import local.foxfoxm3tools.reward.HolyDeviceGuiHooks;
import project.studio.manametalmod.client.GuiOpenBox2;
import project.studio.manametalmod.inventory.ContainerOpenBox;
import project.studio.manametalmod.loot.GuiHolyRelicsBox;
import project.studio.manametalmod.loot.HolyDeviceType;
import project.studio.manametalmod.loot.HolyDeviceSkin;
import project.studio.manametalmod.mob.EntityItemHolyDevice;

/** Test-only mod: actual client classes and FML GUI handler, recording transport. */
@Mod(modid="rewardautoclosetest", name="Isolated Reward Box Test", version="1",
        dependencies="required-after:manametalmod;required-after:foxfoxm3tools")
public final class AutoCloseRuntimeCheck {
    private boolean done;
    private int checks;
    private final List<String> report = new ArrayList<String>();
    private RecordingHandler network;
    private Minecraft mc;
    private EntityClientPlayerMP player;

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) throws Exception {
        mc = Minecraft.func_71410_x();
        if (done || event.phase != TickEvent.Phase.END || !(mc.field_71462_r instanceof GuiMainMenu)) return;
        done = true;
        Path root = Paths.get(System.getProperty("rewardautoclose.test.root"));
        if (!mc.field_71412_D.getCanonicalFile().equals(root.resolve("client").toFile().getCanonicalFile()))
            throw new IllegalStateException("Wrong game directory");
        try {
            local.foxfoxvalidation.MergedIdentity.verify();
            check(mc.field_71441_e == null && mc.field_71439_g == null, "start without a saved world/server");
            check(Loader.isModLoaded("foxfoxm3tools"), "release mod loaded by Forge");
            ModContainer mod = Loader.instance().getIndexedModList().get("foxfoxm3tools");
            NetworkModHolder holder = NetworkRegistry.INSTANCE.registry().get(mod);
            check(holder != null && holder.check(Collections.<String,String>emptyMap(), Side.SERVER),
                    "Forge accepts a server without this client addon");
            report.add("PASS: real client startup and Forge remote-server compatibility check");

            network = new RecordingHandler(mc);
            RecordingWorld world = new RecordingWorld(network, mc);
            player = new EntityClientPlayerMP(mc, world, mc.func_110432_I(), network, new StatFileWriter());
            mc.field_71441_e = world;
            mc.field_71439_g = player;
            mc.field_71442_b = new PlayerControllerMP(mc, network);

            // Drive Forge's real OpenGuiHandler, including its late windowId assignment.
            int windows = 0;
            for (String item : new String[]{"ItemBagBoss1s", "ItemBagBossSky8", "ItemBagBossDungeon_box1", "ItemBagBossSky14"}) {
                for (int count : new int[]{1, 3}) for (boolean render : new boolean[]{false, true}) {
                    int id = 31 + windows++;
                    player.field_71071_by.field_70462_a[0] = new ItemStack(GameRegistry.findItem("manametalmod", item), count);
                    network.closed.clear();
                    new GuiHandler().open(message(id));
                    check(mc.field_71462_r instanceof GuiOpenBox2, "FML opened actual reward screen");
                    check(player.field_71070_bA instanceof ContainerOpenBox && player.field_71070_bA.field_75152_c == id,
                            "FML installed correct container/window ID");
                    check(network.closed.isEmpty(), "no premature close during GuiOpenEvent/initGui");
                    check(count != 1 || player.func_71045_bC() == null, "last box consumed before auto close");
                    if (render) renderStart(); else tickEnd();
                    check(mc.field_71462_r == null, "reward screen immediately closed");
                    check(player.field_71070_bA == player.field_71069_bz, "player inventory container restored");
                    check(network.closed.equals(Collections.singletonList(id)), "one normal close packet for correct window");
                    renderStart(); tickEnd(); renderStart();
                    check(network.closed.size() == 1, "no repeated close packets");
                }
            }
            report.add("PASS: 16 real FML GUI openings, 4 registered box types, single/stacked boxes, render and tick closure, correct close packets");

            network.closed.clear();
            GuiScreen inventory = new GuiInventory(player);
            mc.func_147108_a(inventory); renderStart(); tickEnd();
            check(mc.field_71462_r == inventory && network.closed.isEmpty(), "ordinary inventory stays open");
            GuiScreen chat = new GuiChat();
            mc.func_147108_a(chat);
            // ManaMetal replaces vanilla chat with GuiChatM3 in GuiOpenEvent.
            chat = mc.field_71462_r;
            check(chat != null && chat.getClass().getName().contains("GuiChat"), "chat displayed");
            renderStart(); tickEnd();
            check(mc.field_71462_r == chat && network.closed.isEmpty(), "chat stays open");

            player.field_71071_by.field_70462_a[0] = new ItemStack(GameRegistry.findItem("manametalmod", "ItemBagBoss1s"), 2);
            ContainerOpenBox box = new ContainerOpenBox(player.field_71071_by, Collections.<ItemStack>emptyList(), player);
            GuiOpenBox2 gui = new GuiOpenBox2(box);
            mc.func_147108_a(gui);
            renderStart(); tickEnd();
            check(mc.field_71462_r == gui && network.closed.isEmpty(), "window zero waits for FML setup");
            box.field_75152_c = 91;
            player.field_71070_bA = player.field_71069_bz;
            renderStart(); tickEnd();
            check(mc.field_71462_r == gui && network.closed.isEmpty(), "mismatched container not closed");
            player.field_71070_bA = box;
            // Other mods require consistent world/player state. Test this
            // addon's defensive null guards directly, without their callbacks.
            ClientGuiHooks guards = new ClientGuiHooks();
            mc.field_71439_g = null;
            guards.onRenderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.START, 0));
            guards.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
            mc.field_71439_g = player; mc.field_71441_e = null;
            guards.onRenderTick(new TickEvent.RenderTickEvent(TickEvent.Phase.START, 0));
            guards.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
            check(network.closed.isEmpty(), "no player/world guards");
            mc.field_71441_e = world; renderStart();
            check(network.closed.equals(Collections.singletonList(91)), "close when setup completes");
            report.add("PASS: unrelated screens, pending window ID, changed container, absent player/world, repeated callbacks");
            holyDevices(world);
            report.add("PASS: " + checks + " assertions");
            report.add("LIMIT: real client GUI/FML/packet code and original holy-vessel close/drop code with recording transport/world; no live remote server or saved-world interaction");
            report.add("status=PASS");
        } catch (Throwable failure) {
            StringWriter text = new StringWriter(); failure.printStackTrace(new PrintWriter(text));
            report.add("FAIL: " + text);
        } finally {
            mc.field_71439_g = null; mc.field_71441_e = null; mc.field_71462_r = null;
            mc.field_71442_b = null;
            mc.func_147108_a(new GuiMainMenu());
            Files.write(root.resolve("result.txt"), report, StandardCharsets.UTF_8);
            mc.func_71400_g();
        }
    }

    private void holyDevices(RecordingWorld world) throws Exception {
        player.field_71071_by.field_70462_a[0] = null;
        int openings = 0;
        for (HolyDeviceType type : HolyDeviceType.values()) for (HolyDeviceSkin skin : HolyDeviceSkin.values())
            for (String title : new String[]{"圣器", "聖器", "Holy Vessel", "Artefacto sagrado"})
                for (boolean render : new boolean[]{false, true}) {
                    resetScreen();
                    EntityItemHolyDevice vessel = new EntityItemHolyDevice(world);
                    vessel.type = type; vessel.skintype = skin;
                    int interactions = network.interactions;
                    check(mc.field_71442_b.func_78768_b(player, vessel), "real entity interaction accepted");
                    renderStart(); tickEnd();
                    check(network.closed.isEmpty(), "wait for server without closing window zero");
                    int id = 1 + openings++ % 100;
                    chest(id, title, 54, true);
                    check(mc.field_71462_r instanceof GuiChest && player.field_71070_bA instanceof ContainerChest,
                            "real OpenWindow handler created vanilla chest");
                    check(network.closed.isEmpty(), "holy-vessel init does not close previous container");
                    if (render) renderStart(); else tickEnd();
                    check(mc.field_71462_r == null && player.field_71070_bA == player.field_71069_bz,
                            "holy vessel closes and restores inventory");
                    check(network.closed.equals(Collections.singletonList(id)), "one correct holy-vessel close packet");
                    renderStart(); tickEnd();
                    check(network.closed.size() == 1 && network.interactions == interactions + 1,
                            "no repeated close or automatic entity interaction");
                }
        report.add("PASS: " + openings + " holy-vessel openings through real entity interaction/OpenWindow code, all 3 types and 6 skins, 4 server languages, tick/render closure");

        // A chest's translated name alone does not identify the entity that opened it.
        for (String title : new String[]{"container.chest", "圣器", "Holy Vessel"}) {
            resetScreen(); chest(110, title, 54, true);
            GuiScreen ordinary = mc.field_71462_r; renderStart(); tickEnd();
            check(mc.field_71462_r == ordinary && network.closed.isEmpty(), "ordinary/renamed chest stays open");
        }
        for (int size : new int[]{27, 54}) for (String title : new String[]{"圣器", "Other chest"}) {
            if (size == 54 && title.equals("圣器")) continue;
            arm(world); chest(111, title, size, true);
            GuiScreen unrelated = mc.field_71462_r; renderStart(); tickEnd();
            check(mc.field_71462_r == unrelated && network.closed.isEmpty(), "wrong size/title not closed after interaction");
        }
        arm(world); chest(112, "圣器", 54, false); renderStart(); tickEnd();
        check(mc.field_71462_r != null && network.closed.isEmpty(), "nonliteral inventory name not accepted");

        arm(world);
        player.field_71071_by.field_70462_a[0] = null;
        GuiHolyRelicsBox storage = new GuiHolyRelicsBox(player.field_71071_by);
        mc.func_147108_a(storage); renderStart(); tickEnd();
        check(mc.field_71462_r == storage && network.closed.isEmpty(), "holy relic storage unaffected");
        mc.func_147108_a(null); chest(113, "圣器", 54, true); renderStart(); tickEnd();
        check(network.closed.isEmpty(), "unrelated GUI discarded pending holy-vessel state");

        arm(world);
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent(player,
                PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, 0, 0, 0, 1, world));
        chest(114, "圣器", 54, true); renderStart(); tickEnd();
        check(network.closed.isEmpty(), "block interaction clears pending entity association");

        EntityItemHolyDevice canceled = arm(world);
        EntityInteractEvent canceledEvent = new EntityInteractEvent(player, canceled);
        canceledEvent.setCanceled(true); MinecraftForge.EVENT_BUS.post(canceledEvent);
        chest(115, "圣器", 54, true); renderStart(); tickEnd();
        check(network.closed.isEmpty(), "canceled entity interaction does not arm auto close");

        EntityItemHolyDevice gone = arm(world); gone.func_70106_y();
        chest(116, "圣器", 54, true); renderStart(); tickEnd();
        check(network.closed.isEmpty(), "removed entity clears association");

        arm(world);
        for (int i = 0; i < 100; i++) tickEnd();
        chest(117, "圣器", 54, true); renderStart(); tickEnd();
        check(network.closed.isEmpty(), "expired server reply does not close later chest");

        arm(world);
        GuiChest pending = new GuiChest(player.field_71071_by, new InventoryBasic("圣器", true, 54));
        mc.func_147108_a(pending);
        player.field_71070_bA = pending.field_147002_h;
        renderStart(); tickEnd();
        check(network.closed.isEmpty() && mc.field_71462_r == pending, "holy-vessel window zero waits");
        pending.field_147002_h.field_75152_c = 118;
        player.field_71070_bA = player.field_71069_bz;
        renderStart(); tickEnd();
        check(network.closed.isEmpty(), "holy-vessel mismatched container waits");
        player.field_71070_bA = pending.field_147002_h; renderStart(); tickEnd();
        check(network.closed.equals(Collections.singletonList(118)), "closes only after container/window setup completes");

        // Exercise player/world lifecycle guards without dispatching other mods'
        // tick handlers under deliberately inconsistent world/player state.
        for (boolean removePlayer : new boolean[]{false, true}) {
            resetScreen();
            HolyDeviceGuiHooks hooks = new HolyDeviceGuiHooks();
            hooks.interact(new EntityInteractEvent(player, new EntityItemHolyDevice(world)));
            if (removePlayer) mc.field_71439_g = null; else mc.field_71441_e = null;
            hooks.render(new TickEvent.RenderTickEvent(TickEvent.Phase.START, 0));
            mc.field_71439_g = player; mc.field_71441_e = world;
            chest(119, "圣器", 54, true);
            hooks.open(new net.minecraftforge.client.event.GuiOpenEvent(mc.field_71462_r));
            hooks.render(new TickEvent.RenderTickEvent(TickEvent.Phase.START, 0));
            check(network.closed.isEmpty(), "disconnected player/world does not retain pending association");
        }
        report.add("PASS: ordinary/renamed chests and holy relic storage stay open; size/title, canceled/block interaction, dead entity, timeout, window setup and player/world guards");
        serverDrops(world);
    }

    private EntityItemHolyDevice arm(RecordingWorld world) {
        resetScreen();
        EntityItemHolyDevice entity = new EntityItemHolyDevice(world);
        check(mc.field_71442_b.func_78768_b(player, entity), "arm through actual player controller");
        return entity;
    }

    private void resetScreen() {
        mc.func_147108_a(null);
        player.field_71070_bA = player.field_71069_bz;
        network.closed.clear();
    }

    private void chest(int id, String title, int size, boolean literal) throws IOException {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            new S2DPacketOpenWindow(id, 0, title, size, literal).func_148840_b(buffer);
            S2DPacketOpenWindow decoded = new S2DPacketOpenWindow();
            decoded.func_148837_a(buffer);
            network.func_147265_a(decoded);
        } finally { buffer.release(); }
    }

    private void serverDrops(RecordingWorld world) throws Exception {
        for (boolean full : new boolean[]{false, true}) for (int size : new int[]{0, 1, 54}) {
            resetScreen();
            for (int slot = 0; slot < player.field_71071_by.field_70462_a.length; slot++)
                player.field_71071_by.field_70462_a[slot] = full ? new ItemStack(Items.field_151045_i, 64) : null;
            EntityItemHolyDevice clientVessel = new EntityItemHolyDevice(world);
            mc.field_71442_b.func_78768_b(player, clientVessel);
            EntityItemHolyDevice serverVessel = new EntityItemHolyDevice(world);
            serverVessel.playertemp = player;
            List<ItemStack> expected = new ArrayList<ItemStack>();
            for (int slot = 0; slot < size; slot++) {
                ItemStack item = new ItemStack(Items.field_151043_k, slot % 64 + 1);
                NBTTagCompound tag = new NBTTagCompound(); tag.func_74768_a("testLootSlot", slot);
                item.func_77982_d(tag); serverVessel.item[slot] = item;
                expected.add(item.func_77946_l());
            }
            ContainerChest serverContainer = new ContainerChest(player.field_71071_by, serverVessel);
            int id = 120 + size;
            chest(id, "圣器", 54, true); renderStart(); tickEnd();
            check(network.closed.equals(Collections.singletonList(id)), "server simulation receives exactly one matching close");
            world.drops.clear();
            // Replay the recorded close into the original container/entity code.
            // Only the in-memory spawn sink is replaced, with no saved-world I/O.
            world.captureDrops = true; world.field_72995_K = false;
            try {
                serverContainer.func_75134_a(player);
                check(serverVessel.field_70128_L, "original close removes holy vessel");
                check(world.drops.size() == size, "all remaining stacks released even with full inventory");
                for (int slot = 0; slot < size; slot++) {
                    EntityItem drop = world.drops.get(slot);
                    check(ItemStack.func_77989_b(expected.get(slot), drop.func_92059_d()), "loot count and NBT preserved");
                    check(drop.field_70165_t == player.field_70165_t && drop.field_70163_u == player.field_70163_u
                            && drop.field_70161_v == player.field_70161_v, "loot placed at player position");
                    check(player.func_70005_c_().equals(drop.func_145798_i()) && drop.field_145804_b == 0,
                            "original owner and immediate pickup retained");
                }
                serverContainer.func_75134_a(player);
                check(world.drops.size() == size, "repeated original close cannot duplicate loot");
            } finally { world.field_72995_K = true; world.captureDrops = false; }
        }
        report.add("PASS: original ContainerChest -> holy-vessel close code releases 0/1/54 stacks exactly once, preserves NBT/owner/position, empty and full player inventories");
    }

    private void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    private void renderStart() { FMLCommonHandler.instance().bus().post(new TickEvent.RenderTickEvent(TickEvent.Phase.START, 0)); }
    private void tickEnd() { FMLCommonHandler.instance().bus().post(new TickEvent.ClientTickEvent(TickEvent.Phase.END)); }
    private FMLMessage.OpenGui message(int id) throws Exception {
        Constructor<FMLMessage.OpenGui> ctor = FMLMessage.OpenGui.class.getDeclaredConstructor(
                int.class, String.class, int.class, int.class, int.class, int.class);
        ctor.setAccessible(true);
        return ctor.newInstance(id, "manametalmod", 163, 0, 0, 0);
    }
    public static class GuiHandler extends OpenGuiHandler {
        void open(FMLMessage.OpenGui message) throws Exception { super.channelRead0(null, message); }
    }
    public static class RecordingHandler extends NetHandlerPlayClient {
        final List<Integer> closed = new ArrayList<Integer>();
        int interactions;
        RecordingHandler(Minecraft mc) { super(mc, null, local.foxfoxvalidation.MergedIdentity.offlineNetwork()); }
        @Override public void func_147297_a(Packet packet) {
            if (packet instanceof C02PacketUseEntity) interactions++;
            if (packet instanceof C0DPacketCloseWindow) {
                PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
                try { packet.func_148840_b(buf); closed.add(buf.readUnsignedByte() & 255); }
                catch (IOException e) { throw new RuntimeException(e); }
                finally { buf.release(); }
            }
        }
    }
    public static class RecordingWorld extends WorldClient {
        final List<EntityItem> drops = new ArrayList<EntityItem>();
        boolean captureDrops;
        RecordingWorld(NetHandlerPlayClient network, Minecraft mc) {
            super(network, new WorldSettings(0, WorldSettings.GameType.SURVIVAL, false, false, WorldType.field_77138_c),
                    0, EnumDifficulty.PEACEFUL, mc.field_71424_I);
        }
        @Override public boolean func_72838_d(Entity entity) {
            if (!captureDrops) return super.func_72838_d(entity);
            if (!(entity instanceof EntityItem)) throw new AssertionError("Unexpected spawned entity");
            drops.add((EntityItem) entity);
            return true;
        }
    }
}
