package local.skillrowtest;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.*;
import cpw.mods.fml.common.network.internal.*;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.nbt.*;
import net.minecraft.network.Packet;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.util.StatCollector;
import net.minecraft.world.*;
import org.lwjgl.input.Keyboard;
import project.studio.manametalmod.MMM;
import project.studio.manametalmod.entity.nbt.ManaMetalModRoot;
import project.studio.manametalmod.event.EventGUI;
import project.studio.manametalmod.potion.*;

@Mod(modid="skillrowtest", name="Skill Row Runtime Test", version="1",
        dependencies="required-after:manametalmod;required-after:foxfoxm3tools")
public final class SkillRowRuntimeCheck {
    private boolean done;
    private int checks;
    private Minecraft mc;
    private WorldClient world;
    private EntityClientPlayerMP player;
    private ManaMetalModRoot data;
    private KeyBinding first, second;
    private final List<byte[]> packets = new ArrayList<byte[]>();
    private final List<String> report = new ArrayList<String>();
    private final NBTTagList cases = new NBTTagList();
    @Mod.EventHandler public void init(FMLInitializationEvent e) { FMLCommonHandler.instance().bus().register(this); }
    private void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) throws Exception {
        mc = Minecraft.func_71410_x();
        if (done || e.phase != TickEvent.Phase.END || !(mc.field_71462_r instanceof GuiMainMenu)) return;
        done = true;
        Path fixture = Paths.get(System.getProperty("skillrow.test.root"));
        if (!mc.field_71412_D.getCanonicalFile().equals(fixture.resolve("client").toFile().getCanonicalFile()))
            throw new IllegalStateException("Not an isolated client");
        try {
            local.foxfoxvalidation.MergedIdentity.verify();
            capture(); fresh(); bindings(); rows(); input(); guards();
            NBTTagCompound output = new NBTTagCompound(); output.func_74782_a("Cases", cases);
            try (OutputStream out = Files.newOutputStream(fixture.resolve("packet-cases.nbt"))) {
                CompressedStreamTools.func_74799_a(output, out);
            }
            report.add("LIMIT: key events injected through real KeyBinding API; actual Forge tick listeners and original ManaMetal packet encoder; no physical keyboard or live player/server used.");
            report.add("checks=" + checks); report.add("status=PASS");
        } catch (Throwable failure) {
            StringWriter trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace));
            report.add(trace.toString()); report.add("status=FAIL");
        } finally {
            mc.field_71439_g = null; mc.field_71441_e = null; mc.field_71442_b = null;
            mc.func_147108_a(new GuiMainMenu());
            Files.write(fixture.resolve("result.txt"), report, StandardCharsets.UTF_8); mc.func_71400_g();
        }
    }
    private void capture() {
        for (String name : NetworkRegistry.INSTANCE.channelNamesFor(Side.CLIENT)) {
            FMLEmbeddedChannel channel = NetworkRegistry.INSTANCE.getChannel(name, Side.CLIENT);
            if (channel.pipeline().get(FMLOutboundHandler.class) != null)
                channel.pipeline().replace(FMLOutboundHandler.class, "test-transport", new ChannelOutboundHandlerAdapter() {
                    @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
                        if (message instanceof FMLProxyPacket) {
                            ByteBuf bytes = ((FMLProxyPacket) message).payload().duplicate();
                            if (bytes.readableBytes() == 9 && bytes.readUnsignedByte() == 61) {
                                byte[] body = new byte[8]; bytes.readBytes(body); packets.add(body);
                            }
                        }
                        promise.setSuccess();
                    }
                });
        }
    }
    private void fresh() {
        NetHandlerPlayClient network = new NetHandlerPlayClient(mc, null, local.foxfoxvalidation.MergedIdentity.offlineNetwork()) {
            @Override public void func_147297_a(Packet packet) { }
        };
        world = new WorldClient(network, new WorldSettings(0, WorldSettings.GameType.SURVIVAL, false, false, WorldType.field_77138_c),
                0, EnumDifficulty.PEACEFUL, mc.field_71424_I);
        player = new EntityClientPlayerMP(mc, world, mc.func_110432_I(), network, new StatFileWriter());
        mc.field_71441_e = world; mc.field_71439_g = player; mc.field_71442_b = new PlayerControllerMP(mc, network);
        mc.field_71462_r = null; mc.field_71415_G = true;
        data = MMM.getEntityNBT(player);
        check(data != null && data.carrer != null, "original career NBT initialized");
    }
    private void bindings() throws Exception {
        int count = 0;
        for (KeyBinding key : mc.field_71474_y.field_74324_K) {
            if (key.func_151464_g().equals("key.foxfoxm3tools.skillRow1")) { first = key; count++; }
            if (key.func_151464_g().equals("key.foxfoxm3tools.skillRow2")) { second = key; count++; }
        }
        check(count == 2 && first != null && second != null, "exactly two keys in actual controls settings");
        check(first.func_151463_i() == 0 && second.func_151463_i() == 0, "both default unbound");
        check(StatCollector.func_74838_a(first.func_151464_g()).equals("一键释放组合技能栏第一行"), "row one Chinese label");
        check(StatCollector.func_74838_a(second.func_151464_g()).equals("一键释放组合技能栏第二行"), "row two Chinese label");
        check(StatCollector.func_74838_a(first.func_151466_e()).equals("狐狐魔金小工具"), "Chinese controls category");
        bind(first, Keyboard.KEY_F7); bind(second, Keyboard.KEY_F8);
        mc.field_71474_y.func_74303_b();
        first.func_151462_b(0); second.func_151462_b(0); mc.field_71474_y.func_74300_a(); KeyBinding.func_74508_b();
        check(first.func_151463_i() == Keyboard.KEY_F7 && second.func_151463_i() == Keyboard.KEY_F8, "vanilla save/load preserves bindings");
        report.add("PASS: two unique controls entries, Chinese labels/category, default unbound, remapping and options persistence");
    }
    private void bind(KeyBinding key, int code) { key.func_151462_b(code); KeyBinding.func_74508_b(); }
    private void step() { FMLCommonHandler.instance().bus().post(new TickEvent.ClientTickEvent(TickEvent.Phase.END)); }
    private void down(KeyBinding key) { KeyBinding.func_74510_a(key.func_151463_i(), true); KeyBinding.func_74507_a(key.func_151463_i()); }
    private void release() { KeyBinding.func_74506_a(); step(); }
    private void press(KeyBinding key) { release(); packets.clear(); down(key); step(); }
    private void expect(int... ids) throws Exception {
        check(packets.size() == ids.length, "packet count: expected " + ids.length + " actual " + packets.size());
        for (int i = 0; i < ids.length; i++) {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(packets.get(i)));
            check(in.readInt() == ids[i] && in.readInt() == 0, "original cast packet ID/data and left-to-right order at " + i);
        }
    }
    private void export(String name) {
        NBTTagCompound test = new NBTTagCompound(); test.func_74778_a("Name", name);
        NBTTagList list = new NBTTagList();
        for (byte[] body : packets) { NBTTagCompound packet = new NBTTagCompound(); packet.func_74773_a("Body", body); list.func_74742_a(packet); }
        test.func_74782_a("Packets", list); cases.func_74742_a(test);
    }
    private void rows() throws Exception {
        data.carrer.spellKey_1 = new int[]{306, 305, 304, 303, 207, 107, 100};
        data.carrer.spellKey_2 = new int[]{102, 103, 104, 201, 202, 301, 302};
        data.carrer.spellKey_3 = new int[]{302, 301, 202, 201, 104, 103, 102};
        int[] original = data.carrer.spellKey_1.clone();
        int mana = data.mana.getMana(); int[] cd = data.carrer.spellCD_LV1.clone();
        press(first); expect(102, 103, 104, 201, 202, 301, 302); export("row1");
        press(second); expect(302, 301, 202, 201, 104, 103, 102); export("row2");
        check(Arrays.equals(original, data.carrer.spellKey_1), "immediate row never modified");
        check(mana == data.mana.getMana() && Arrays.equals(cd, data.carrer.spellCD_LV1), "no local mana/cooldown mutation");
        data.carrer.spellKey_2 = new int[]{-1, 104, -1, 102, -1, 103, -1}; press(first); expect(104, 102, 103);
        data.carrer.spellKey_2 = new int[]{-1, -1, -1, -1, -1, -1, -1}; press(first); expect();
        data.carrer.spellKey_2 = new int[]{102, 102, 103, 104, 103, -1, 102}; press(first); expect(102, 103, 104);
        data.carrer.spellKey_2 = new int[]{1, 2, 3, 0, -1, -2, 99}; press(first); expect();
        data.carrer.spellKey_2 = null; press(first); expect();
        data.carrer.spellKey_2 = new int[]{103}; press(first); expect(103);
        data.carrer.spellKey_2 = new int[]{104, -1, -1, -1, -1, -1, -1, 306}; press(first); expect(104);
        report.add("PASS: all seven positions in both rows, reverse order, updated row data, immediate row untouched, empty/duplicate/malformed slots, no client-side mana/CD changes; exported real packets for server replay");
    }
    private void input() throws Exception {
        data.carrer.spellKey_2 = new int[]{102}; data.carrer.spellKey_3 = new int[]{103};
        press(first); expect(102);
        for (int i = 0; i < 30; i++) { down(first); step(); }
        expect(102); release(); down(first); step(); expect(102, 102);
        release(); packets.clear(); down(first); down(second); step(); expect(102, 103);
        release(); packets.clear(); down(first); down(first); down(first); step(); expect(102);
        release(); packets.clear(); down(second); KeyBinding.func_74510_a(second.func_151463_i(), false); step(); expect(103);
        release(); bind(first, -98); press(first); expect(102); release(); bind(first, Keyboard.KEY_F7);
        packets.clear(); down(first); FMLCommonHandler.instance().bus().post(new TickEvent.ClientTickEvent(TickEvent.Phase.START)); expect(); step(); expect(102);
        report.add("PASS: held/repeat input does not recast, release then press, simultaneous independent keys, queued repeats collapsed, short taps, mouse binding, only one tick phase");
    }
    private void guards() throws Exception {
        for (int mode = 0; mode < 7; mode++) {
            release(); packets.clear();
            if (mode == 0) mc.field_71462_r = new GuiChat();
            if (mode == 1) mc.field_71462_r = new GuiControls(new GuiMainMenu(), mc.field_71474_y);
            if (mode == 2) mc.field_71415_G = false;
            if (mode == 3) { mc.field_71439_g = null; mc.field_71441_e = null; }
            if (mode == 4) mc.field_71441_e = null;
            if (mode == 5) player.field_70128_L = true;
            if (mode == 6) EventGUI.disableKeyboard = 5;
            down(first); step(); expect();
            mc.field_71462_r = null; mc.field_71415_G = true; mc.field_71439_g = player; mc.field_71441_e = world;
            player.field_70128_L = false; EventGUI.disableKeyboard = 0;
            step(); expect(); down(first); step(); expect();
            press(first); expect(102);
        }
        release(); data.ManaEntityData.potions.add(new PotionEffectM3(PotionM3.potionGosh, 5, 0));
        check(!MMM.canUpdate(player), "real crowd-control restriction active");
        press(first); expect(); data.ManaEntityData.potions.clear();
        press(first); expect(102);
        release(); data.carrer.isDead = true; press(first); expect();
        data.carrer.isDead = false; mc.field_71462_r = null; mc.field_71415_G = true; press(first); expect(102);
        release(); bind(first, 0); packets.clear(); KeyBinding.func_74507_a(0); step(); expect(); bind(first, Keyboard.KEY_F7);
        report.add("PASS: chat, controls GUI, lost focus, disconnect, absent world, death, original keyboard lock and crowd control discard input; no deferred casts on resume; unbound key inactive");
    }
}
