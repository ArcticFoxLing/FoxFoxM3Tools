package local.beehiveclienttest;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.NetworkModHolder;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.Unpooled;
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
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.opengl.GL11;
import project.studio.manametalmod.produce.beekeeping.*;

@Mod(modid="beehiveclienttest", name="Beehive Client Test", version="1",
        dependencies="required-after:manametalmod;required-after:foxfoxm3tools;after:NotEnoughItems")
public final class BeehiveRuntimeCheck {
    private boolean done;
    private int checks;
    private final List<String> report = new ArrayList<String>();
    private final NBTTagList cases = new NBTTagList();
    private Minecraft mc;
    private EntityClientPlayerMP player;
    private RecordingHandler network;
    private TileEntityBeehive hive;
    private ContainerHoneycomb container;
    private Guihoneycomb gui;
    private Path root;

    @Mod.EventHandler public void init(FMLInitializationEvent event) { FMLCommonHandler.instance().bus().register(this); }
    private void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) throws Exception {
        mc = Minecraft.func_71410_x();
        if (done || event.phase != TickEvent.Phase.END || !(mc.field_71462_r instanceof GuiMainMenu)) return;
        done = true;
        root = Paths.get(System.getProperty("beehiveclient.test.root"));
        if (!mc.field_71412_D.getCanonicalFile().equals(root.resolve("client").toFile().getCanonicalFile()))
            throw new IllegalStateException("Not the isolated client");
        try {
            local.foxfoxvalidation.MergedIdentity.verify();
            ModContainer mod = Loader.instance().getIndexedModList().get("foxfoxm3tools");
            NetworkModHolder holder = NetworkRegistry.INSTANCE.registry().get(mod);
            check(holder.check(Collections.<String,String>emptyMap(), Side.SERVER), "server addon not required");
            check(!Loader.isModLoaded("manametalbeehiveauto"), "old server auto mod absent");
            network = new RecordingHandler(mc);
            WorldClient world = new WorldClient(network,
                    new WorldSettings(0, WorldSettings.GameType.SURVIVAL, false, false, WorldType.field_77138_c),
                    0, EnumDifficulty.PEACEFUL, mc.field_71424_I);
            player = new EntityClientPlayerMP(mc, world, mc.func_110432_I(), network, new StatFileWriter());
            mc.field_71441_e = world; mc.field_71439_g = player;
            mc.field_71442_b = new PlayerControllerMP(mc, network);
            report.add("PASS: actual merged Forge client startup; server addon optional; standalone beehive mods absent");

            for (int first : new int[]{0, 1, 11, 12}) for (int second : new int[]{0, 1, 11, 12})
                for (int bees : new int[]{0, 1, 7, 24, 40}) {
                    setup(640, 360);
                    hive.items[0] = tagged(new ItemStack(BeekeepingCore.beequeen1, 1, 2), "queen");
                    if (first > 0) hive.items[1] = new ItemStack(BeekeepingCore.beebase, first);
                    if (second > 0) hive.items[2] = new ItemStack(BeekeepingCore.beebase, second);
                    supply(bees);
                    hive.items[3] = new ItemStack(BeekeepingCore.honeycomb, 8, 1);
                    NBTTagCompound before = snapshot();
                    update();
                    check(network.clicks.isEmpty() && before.equals(snapshot()), "opening/ticking has no automatic effect");
                    check(buttons().size() == 2, "exactly two buttons");
                    click(false);
                    int filled = Math.min(24, first + second + bees);
                    check(hive.beeCount() == filled, "fills to cap or available workers");
                    check(playerBees() == bees - filled + first + second, "deducts exact worker count");
                    check(player.field_71071_by.func_70445_o() == null, "no worker stuck on cursor");
                    check(hive.items[0].func_77960_j() == 2 && hive.items[3].field_77994_a == 8, "refill leaves queen/products intact");
                    for (C0EPacketClickWindow packet : network.clicks)
                        check(packet.func_149542_h() == 0 && packet.func_149544_d() != 0 && packet.func_149544_d() != 3,
                                "refill uses ordinary pickup/place, never queen/output");
                    saveCase("refill", before);
                    int sent = network.clicks.size();
                    click(false);
                    check(network.clicks.size() == sent, "repeated refill is a no-op when done");
                }
            report.add("PASS: 80 real-button refill cases: empty/partial/full hives, insufficient/excess inventory, ordinary C0E clicks");

            setup(640, 360);
            hive.items[0] = tagged(new ItemStack(BeekeepingCore.beequeen1, 1, 1), "queen");
            hive.items[1] = new ItemStack(BeekeepingCore.beebase, 4);
            supply(30);
            for (int slot = 3; slot < 27; slot++) hive.items[slot] = tagged(
                    new ItemStack(slot % 2 == 0 ? BeekeepingCore.honeycomb : BeekeepingCore.beelarva,
                            slot % 2 == 0 ? 8 : 1, slot % 6), "output " + slot);
            NBTTagCompound before = snapshot();
            update();
            draw("beehive-buttons.png");
            click(true);
            check(network.clicks.size() == 24, "one click sends all 24 whole-stack drops");
            for (C0EPacketClickWindow packet : network.clicks)
                check(packet.func_149542_h() == 4 && packet.func_149543_e() == 1
                        && packet.func_149544_d() >= 3 && packet.func_149544_d() <= 26, "Ctrl+Q output slots only");
            for (int slot = 3; slot < 27; slot++) check(hive.items[slot] == null, "all outputs emptied");
            check(hive.beeCount() == 4 && hive.items[0] != null && playerBees() == 30, "drop leaves all bees untouched");
            saveCase("drop", before);
            int count = network.clicks.size(); click(true);
            check(network.clicks.size() == count, "repeated drop does not resend empty slots");
            report.add("PASS: drop button only drops 24 output slots; all input bees/queen preserved; screenshot rendered");

            setup(320, 240);
            hive.items[1] = tagged(new ItemStack(BeekeepingCore.beebase, 10), "A");
            hive.items[2] = tagged(new ItemStack(BeekeepingCore.beebase, 9), "B");
            player.field_71071_by.field_70462_a[9] = tagged(new ItemStack(BeekeepingCore.beebase, 12), "B");
            player.field_71071_by.field_70462_a[10] = tagged(new ItemStack(BeekeepingCore.beebase, 12), "A");
            before = snapshot(); click(false);
            check(hive.beeCount() == 24 && player.field_71071_by.field_70462_a[9].field_77994_a == 9
                    && player.field_71071_by.field_70462_a[10].field_77994_a == 10, "tag matching and remainder return");
            saveCase("refill-tagged", before);
            for (GuiButton button : buttons()) check(button.field_146128_h + button.field_146120_f <= 320
                    && button.field_146129_i + button.field_146121_g <= 240
                    && button.field_146128_h >= (320 - 186) / 2 + 186, "small GUI buttons stay outside slots/on screen");
            draw("beehive-small.png");

            setup(280, 240); supply(24); hive.items[3] = new ItemStack(BeekeepingCore.honeycomb, 8);
            update();
            for (GuiButton button : buttons())
                check(button.field_146128_h >= 0 && button.field_146128_h + button.field_146120_f <= 280
                        && button.field_146129_i >= (240 - 155) / 2 + 155
                        && button.field_146129_i + button.field_146121_g <= 240,
                        "narrow layout moves both buttons below slots and within screen");
            draw("beehive-narrow.png");

            setup(640, 360); hive.items[2] = new ItemStack(BeekeepingCore.beebase, 11); supply(2);
            before = snapshot(); click(false);
            check(hive.items[2].field_77994_a == 12 && hive.items[1].field_77994_a == 1, "existing stack first");
            saveCase("refill-priority", before);
            setup(640, 360);
            for (int slot = 0; slot < 36; slot++) player.field_71071_by.field_70462_a[slot] = new ItemStack(Items.field_151055_y, 64);
            hive.items[3] = tagged(new ItemStack(BeekeepingCore.beelarva, 1, 4), "larva");
            before = snapshot(); click(true); saveCase("drop-full-inventory", before);
            check(hive.items[3] == null, "full inventory still drops");
            report.add("PASS: NBT matching, surplus returned to source, refill priority, full inventory, 320x240 and 280x240 layouts");

            setup(640, 360); supply(24); hive.items[3] = new ItemStack(BeekeepingCore.honeycomb, 8);
            player.field_71071_by.func_70437_b(new ItemStack(Items.field_151045_i, 3));
            before = snapshot(); click(true); click(false);
            check(network.clicks.isEmpty() && before.equals(snapshot()), "occupied cursor disables both actions");
            player.field_71071_by.func_70437_b(null);
            container.field_75152_c = 0; click(true); click(false);
            check(network.clicks.isEmpty(), "pending window ID disabled");
            container.field_75152_c = 7;
            player.field_71070_bA = player.field_71069_bz; click(true); click(false);
            check(network.clicks.isEmpty(), "stale container disabled");
            player.field_71070_bA = container;
            GuiScreen other = new GuiScreen(); mc.field_71462_r = other;
            other.func_146280_a(mc, 640, 360);
            check(((List<?>)ReflectionHelper.getPrivateValue(GuiScreen.class, other, "field_146292_n", "buttonList")).isEmpty(), "unrelated GUI has no buttons");
            setup(640, 360); gui.func_146280_a(mc, 640, 360);
            check(buttons().size() == 2, "resize/reinit does not duplicate buttons");
            report.add("PASS: occupied cursor, pending window, stale container, unrelated GUI, resize guards");

            NBTTagCompound data = new NBTTagCompound(); data.func_74782_a("Cases", cases);
            try (OutputStream out = Files.newOutputStream(root.resolve("packet-cases.nbt"))) { CompressedStreamTools.func_74799_a(data, out); }
            report.add("PASS: exported " + cases.func_74745_c() + " actual client click sequences for server replay");
            report.add("PASS: " + checks + " assertions; ManaMetal " + Loader.instance().getIndexedModList().get("manametalmod").getVersion());
            report.add("status=PASS");
        } catch (Throwable failure) {
            StringWriter trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace)); report.add("status=FAIL\n" + trace);
        } finally {
            mc.field_71439_g = null; mc.field_71441_e = null; mc.field_71462_r = null; mc.field_71442_b = null;
            mc.func_147108_a(new GuiMainMenu());
            Files.write(root.resolve("result.txt"), report, StandardCharsets.UTF_8);
            mc.func_71400_g();
        }
    }

    private void setup(int width, int height) {
        Arrays.fill(player.field_71071_by.field_70462_a, null);
        player.field_71071_by.func_70437_b(null);
        hive = new TileEntityBeehive();
        container = new ContainerHoneycomb(player.field_71071_by, hive); container.field_75152_c = 7;
        player.field_71070_bA = container;
        gui = new Guihoneycomb(container); mc.func_147108_a(gui);
        gui.func_146280_a(mc, width, height);
        network.clicks.clear();
    }
    @SuppressWarnings("unchecked") private List<GuiButton> buttons() {
        return ReflectionHelper.getPrivateValue(GuiScreen.class, gui, "field_146292_n", "buttonList");
    }
    private void update() { FMLCommonHandler.instance().bus().post(new TickEvent.ClientTickEvent(TickEvent.Phase.END)); }
    private void click(boolean drop) {
        update();
        GuiButton button = buttons().get(drop ? 0 : 1);
        check(button.field_146126_j.equals(drop ? "丢出产物" : "补充蜜蜂"), "localized button label");
        MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.ActionPerformedEvent.Pre(gui, button, buttons()));
        check(mc.field_71462_r == gui, "GUI remains open");
    }
    private void supply(int count) {
        for (int slot : new int[]{9, 17, 35, 8}) {
            if (count == 0) break;
            int amount = Math.min(count, 12); player.field_71071_by.field_70462_a[slot] = new ItemStack(BeekeepingCore.beebase, amount); count -= amount;
        }
    }
    private int playerBees() {
        int result = 0;
        for (ItemStack item : player.field_71071_by.field_70462_a)
            if (item != null && item.func_77973_b() == BeekeepingCore.beebase) result += item.field_77994_a;
        return result;
    }
    private static ItemStack tagged(ItemStack item, String name) {
        item.func_151001_c(name); item.func_77978_p().func_74783_a("BeeEffect", new int[]{1, 3, 5}); return item;
    }
    private static NBTTagCompound item(ItemStack stack) {
        NBTTagCompound tag = new NBTTagCompound();
        if (stack != null) {
            tag.func_74778_a("Item", Item.field_150901_e.func_148750_c(stack.func_77973_b()));
            tag.func_74768_a("Count", stack.field_77994_a); tag.func_74768_a("Damage", stack.func_77960_j());
            if (stack.func_77942_o()) tag.func_74782_a("Tag", stack.func_77978_p().func_74737_b());
        }
        return tag;
    }
    private NBTTagCompound snapshot() {
        NBTTagCompound tag = new NBTTagCompound(); NBTTagList slots = new NBTTagList();
        for (int slot = 0; slot < 63; slot++) slots.func_74742_a(item(container.func_75139_a(slot).func_75211_c()));
        tag.func_74782_a("Slots", slots); tag.func_74782_a("Cursor", item(player.field_71071_by.func_70445_o())); return tag;
    }
    private void saveCase(String label, NBTTagCompound before) {
        NBTTagCompound test = new NBTTagCompound(); test.func_74778_a("Name", label);
        test.func_74782_a("Before", before); test.func_74782_a("After", snapshot());
        NBTTagList packets = new NBTTagList();
        for (C0EPacketClickWindow packet : network.clicks) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.func_74768_a("Window", packet.func_149548_c()); entry.func_74768_a("Slot", packet.func_149544_d());
            entry.func_74768_a("Button", packet.func_149543_e()); entry.func_74768_a("Mode", packet.func_149542_h());
            entry.func_74777_a("Action", packet.func_149547_f()); entry.func_74782_a("Clicked", item(packet.func_149546_g()));
            packets.func_74742_a(entry);
        }
        test.func_74782_a("Packets", packets); cases.func_74742_a(test);
    }
    private void draw(String name) {
        mc.func_147110_a().func_147610_a(true);
        GL11.glClearColor(.09F, .105F, .13F, 1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity();
        GL11.glOrtho(0, gui.field_146294_l, gui.field_146295_m, 0, 1000, 3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity(); GL11.glTranslatef(0, 0, -2000);
        gui.func_73863_a(0, 0, 0);
        ScreenShotHelper.func_148259_a(root.toFile(), name, mc.field_71443_c, mc.field_71440_d, mc.func_147110_a());
    }
    public static final class RecordingHandler extends NetHandlerPlayClient {
        final List<C0EPacketClickWindow> clicks = new ArrayList<C0EPacketClickWindow>();
        RecordingHandler(Minecraft mc) { super(mc, null, new NetworkManager(true) {
            @Override public java.net.SocketAddress func_74430_c() { return new java.net.InetSocketAddress("127.0.0.1", 0); }
            @Override public void func_150725_a(Packet packet, io.netty.util.concurrent.GenericFutureListener... listeners) {}
        }); }
        @Override public void func_147297_a(Packet packet) {
            if (!(packet instanceof C0EPacketClickWindow)) return;
            PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
            try {
                packet.func_148840_b(buffer);
                C0EPacketClickWindow copy = new C0EPacketClickWindow(); copy.func_148837_a(buffer); clicks.add(copy);
            } catch (IOException error) { throw new IllegalStateException(error); }
            finally { buffer.release(); }
        }
    }
}
