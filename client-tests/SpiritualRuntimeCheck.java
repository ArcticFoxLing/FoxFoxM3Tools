package project.studio.manametalmod.client;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.FMLEmbeddedChannel;
import cpw.mods.fml.common.network.FMLOutboundHandler;
import cpw.mods.fml.common.network.internal.*;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.*;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.*;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.opengl.GL11;
import project.studio.manametalmod.MMM;
import project.studio.manametalmod.core.DayResource;
import project.studio.manametalmod.core.SpiritualPower;
import project.studio.manametalmod.entity.nbt.ManaMetalModRoot;
import project.studio.manametalmod.itemAndBlockCraft.ItemCraft2;

/** Test-only mod: actual Forge/ManaMetal GUI and network encoder, simulated replies. */
@Mod(modid="spiritualautoresettest", name="Isolated Spiritual Auto Reset Test", version="1",
        dependencies="required-after:manametalmod;required-after:foxfoxm3tools")
public final class SpiritualRuntimeCheck {
    private boolean done;
    private int checks;
    private final List<String> report = new ArrayList<String>();
    private Minecraft mc;
    private RecordingHandler network;
    private EntityClientPlayerMP player;
    private ManaMetalModRoot data;
    private GuiSpiritualPower gui;
    private Path fixture;
    private final Field buttonList = ReflectionHelper.findField(GuiScreen.class, "field_146292_n", "buttonList");

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) throws Exception {
        mc = Minecraft.func_71410_x();
        if (done || event.phase != TickEvent.Phase.END || !(mc.field_71462_r instanceof GuiMainMenu)) return;
        done = true;
        fixture = Paths.get(System.getProperty("spiritualautoreset.test.root"));
        if (!mc.field_71412_D.getCanonicalFile().equals(fixture.resolve("client").toFile().getCanonicalFile()))
            throw new IllegalStateException("Wrong test directory");
        try {
            local.foxfoxvalidation.MergedIdentity.verify();
            check(mc.field_71439_g == null && mc.field_71441_e == null, "no live world or server");
            ModContainer mod = Loader.instance().getIndexedModList().get("foxfoxm3tools");
            NetworkModHolder holder = NetworkRegistry.INSTANCE.registry().get(mod);
            check(holder != null && holder.check(Collections.<String,String>emptyMap(), Side.SERVER),
                    "server does not need the client addon");
            // Replace only test transport, after the real message encoder.
            for (String name : NetworkRegistry.INSTANCE.channelNamesFor(Side.CLIENT)) {
                FMLEmbeddedChannel channel = NetworkRegistry.INSTANCE.getChannel(name, Side.CLIENT);
                if (channel.pipeline().get(FMLOutboundHandler.class) != null)
                    channel.pipeline().replace(FMLOutboundHandler.class, "test-recording-transport",
                            new ChannelOutboundHandlerAdapter() {
                        @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
                            if (network != null && message instanceof Packet) network.func_147297_a((Packet)message);
                            promise.setSuccess();
                        }
                    });
            }

            fresh(120, 10000000, 64);
            check(buttons().size() == 22, "8 original, 8 auto and 6 shared quality buttons");
            check(target(6).field_146126_j.contains("["), "default target is ultimate");
            for (int s = 0; s < 8; s++) {
                GuiButton auto = auto(s), original = gui.set[s];
                check(auto.field_146128_h == original.field_146128_h + original.field_146120_f + 2,
                        "auto beside original stage " + s);
                check(auto.field_146129_i == original.field_146129_i, "row alignment " + s);
            }
            render("eight-stages.png", -1);
            gui.func_146280_a(mc, 320, 240);
            check(buttons().size() == 22, "resize does not duplicate buttons");
            check(auto(7).field_146128_h + auto(7).field_146120_f <= 320, "buttons fit minimum GUI width");
            for (int q = 1; q <= 6; q++) {
                GuiButton choice = target(q);
                check(choice.field_146129_i >= 0 && choice.field_146129_i + choice.field_146121_g <= gui.guiTop + 29,
                        "target row above first stage and on screen " + q);
                check(choice.field_146128_h >= 0 && choice.field_146128_h + choice.field_146120_f <= 320,
                        "target row fits minimum width " + q);
            }
            render("minimum-size.png", -1);
            report.add("PASS: 8 buttons, original actions retained, minimum 320x240 layout, resize, screenshots");

            for (int stage = 0; stage < 8; stage++) {
                fresh(120, 10000000, 64);
                click(auto(stage)); step(1);
                check(network.resets.equals(Collections.singletonList(stage)), "one first request stage " + stage);
                check(data.carrer.SpiritualPower[stage] == 0, "original animation clears display");
                step(35);
                check(network.resets.size() == 1, "animation finish is not server acknowledgement");
                reply(stage, 1, true); step(1);
                check(network.resets.size() == 2, "identical quality continues stage " + stage);
                reply(stage, 5, true); step(16);
                check(network.resets.size() == 3, "legendary quality continues stage " + stage);
                reply(stage, 6, true); step(40);
                check(network.resets.size() == 3 && data.carrer.SpiritualPower[stage] == 6,
                        "terminal quality preserved stage " + stage);
                check(!auto(stage).field_146124_l, "ultimate auto button disabled");
                for (int s = 0; s < 8; s++) if (s != stage)
                    check(data.carrer.SpiritualPower[s] == 1, "other stages unchanged");
            }
            report.add("PASS: all 8 stages, repeated same quality, legendary continues, ultimate stops without an extra reset");

            fresh(120, 10000000, 64);
            click(auto(2)); step(1); render("running.png", 2);
            click(auto(2)); reply(2, 4, true); step(35);
            check(network.resets.size() == 1, "stop button prevents another request");
            fresh(120, 10000000, 64);
            click(auto(3)); step(1);
            mc.func_147108_a(null); step(2);
            gui = new GuiSpiritualPower(); mc.func_147108_a(gui);
            check(!auto(3).field_146124_l, "reopen retains outstanding request barrier");
            reply(3, 6, true); step(35);
            check(network.resets.size() == 1, "close/reopen never resumes automation");
            fresh(120, 10000000, 64);
            click(auto(1)); step(1); gui.lock[1] = true;
            reply(1, 4, true); step(35);
            check(network.resets.size() == 1, "locking running stage stops it");
            report.add("PASS: stop, close/reopen, lock, no concurrent stage requests");

            fresh(120, 10000000, 1);
            click(auto(0)); step(1); reply(0, 5, true); step(35);
            check(network.resets.size() == 1 && !auto(0).field_146124_l, "last gem stops non-ultimate run");
            fresh(120, 500, 64);
            click(auto(0)); step(1); reply(0, 5, true); step(35);
            check(network.resets.size() == 1, "insufficient power stops");
            fresh(120, 1020, 64);
            click(auto(0)); step(1); reply(0, 5, true); step(35);
            check(network.resets.size() == 1, "cost increase after first reset is respected");
            fresh(30, 10000000, 64);
            for (int s = 1; s < 8; s++) check(!auto(s).field_146124_l, "level requirement stage " + s);
            fresh(120, 10000000, 0);
            check(!auto(0).field_146124_l, "no gem cannot start");
            fresh(120, 10000000, 64); data.carrer.SpiritualPower[0] = 6; step(1);
            check(!auto(0).field_146124_l && network.resets.isEmpty(), "existing ultimate is preserved");
            report.add("PASS: gem exhaustion, insufficient power, growing daily cost, level locks, existing ultimate");

            fresh(120, 10000000, 64);
            click(auto(0)); step(1); reply(0, 5, false); step(40);
            check(network.resets.size() == 1, "wait for delayed inventory sync");
            player.field_71071_by.field_70462_a[0].field_77994_a--;
            step(1); check(network.resets.size() == 2, "continue when inventory catches up");
            fresh(120, 10000000, 64);
            click(auto(0)); step(220);
            check(network.resets.size() == 1, "timeout never resends");
            reply(0, 4, true); step(35);
            check(network.resets.size() == 1 && auto(0).field_146124_l, "late reply releases barrier without resuming");
            fresh(120, 10000000, 64);
            click(gui.set[4]); step(40);
            check(network.resets.equals(Collections.singletonList(4)) && !auto(4).field_146124_l,
                    "manual request tracked until response");
            reply(4, 5, true); step(1); click(auto(4)); step(1);
            check(network.resets.size() == 2, "auto available after manual response");
            reply(4, 6, true); step(35);
            check(network.resets.size() == 2, "manual then auto ultimate stops");
            fresh(120, 10000000, 64);
            data.carrer.setDayResource(DayResource.SpiritualPower, 100);
            click(auto(7)); step(1); reply(7, 5, true); step(16);
            check(network.resets.size() == 2, "acknowledgement works at daily cost cap");
            reply(7, 6, true); step(25);
            report.add("PASS: delayed/absent replies, delayed inventory, manual-to-auto transition, daily cost cap");

            fresh(120, 10000000, 64);
            click(auto(0)); step(1);
            // Resize while running does not start over or lose Stop.
            gui.func_146280_a(mc, 640, 360);
            check(buttons().size() == 22 && auto(0).field_146124_l, "running survives resize with stop available");
            reply(0, 6, true); step(30);
            render("ultimate-stopped.png", 0);
            check(network.resets.size() == 1, "resize did not duplicate request");
            mc.func_147108_a(new GuiScreen()); step(20);
            check(network.resets.size() == 1, "unrelated screen unaffected");

            for (int quality = 1; quality <= 6; quality++) for (int stage = 0; stage < 8; stage++) {
                fresh(120, 10000000, quality == 6 ? 64 : 0);
                data.carrer.SpiritualPower[stage] = quality == 1 ? 2 : 1;
                click(target(quality));
                check(network.resets.isEmpty(), "selector does not send reset " + quality + "/" + stage);
                check(target(quality).field_146126_j.contains("["), "selected target is marked");
                check(Arrays.equals(gui.lock, new boolean[8]), "selector does not toggle stage locks");
                click(auto(stage)); step(1);
                check(network.resets.equals(Collections.singletonList(stage)), "selected target starts stage " + quality + "/" + stage);
                reply(stage, quality == 6 ? 5 : quality + 1, true); step(16);
                check(network.resets.size() == 2, "different quality including higher quality keeps rolling " + quality + "/" + stage);
                reply(stage, quality, true); step(35);
                check(network.resets.size() == 2 && data.carrer.SpiritualPower[stage] == quality,
                        "exact target stops with no extra reset " + quality + "/" + stage);
                check(!auto(stage).field_146124_l, "matching stage auto disabled");
            }
            report.add("PASS: all 48 quality/stage combinations, exact matching, higher qualities rerolled, non-ultimate targets without gems");

            fresh(120, 10000000, 0);
            click(target(4));
            click(auto(0)); step(1); reply(0, 4, true); step(20);
            click(auto(7)); step(1); reply(7, 4, true); step(20);
            check(network.resets.equals(Arrays.asList(0, 7)), "different stages share the top target without selecting again");
            check(!auto(0).field_146124_l && !auto(7).field_146124_l, "both stages match global target");
            gui.func_146280_a(mc, 320, 240);
            check(buttons().size() == 22 && target(4).field_146126_j.contains("["), "target retained on resize");
            render("selected-epic-minimum.png", -1);
            mc.func_147108_a(null); step(1);
            gui = new GuiSpiritualPower(); mc.func_147108_a(gui);
            check(target(4).field_146126_j.contains("[") && network.resets.size() == 2, "target retained on reopen, no auto restart");
            render("shared-target-epic.png", 7);

            fresh(120, 10000000, 64);
            click(auto(0)); step(1); click(target(4));
            check(network.resets.size() == 1 && auto(0).field_146124_l, "changing target keeps one request and stop available");
            reply(0, 6, true); step(16);
            check(network.resets.size() == 2, "in-flight reply evaluated against new target");
            click(target(2)); reply(0, 2, true); step(35);
            check(network.resets.size() == 2 && !auto(0).field_146124_l, "new target result stops active run");
            fresh(120, 10000000, 0);
            click(target(2)); click(auto(1)); step(1); click(target(6)); reply(1, 3, true); step(35);
            check(network.resets.size() == 1, "switching to ultimate without gems stops after outstanding request");
            fresh(120, 10000000, 0);
            click(target(5)); click(auto(2)); step(1); reply(2, 4, true); step(1);
            click(target(4)); step(30);
            check(network.resets.size() == 1, "switch to current quality during animation stops before another request");
            report.add("PASS: shared selection across stages and screen reopen, changing target during requests/animation, updated gem requirement");
            check(GL11.glGetError() == GL11.GL_NO_ERROR, "rendering has no OpenGL error");
            report.add("PASS: " + checks + " assertions");
            report.add("LIMIT: actual Forge/ManaMetal client, GUI input, animation and outbound packet encoding; server replies and inventory sync simulated in an isolated fixture. No live server used.");
            report.add("status=PASS");
        } catch (Throwable failure) {
            StringWriter text = new StringWriter(); failure.printStackTrace(new PrintWriter(text));
            report.add("FAIL: " + text);
        } finally {
            mc.field_71462_r = null; mc.field_71439_g = null; mc.field_71441_e = null; mc.field_71442_b = null;
            mc.func_147108_a(new GuiMainMenu());
            Files.write(fixture.resolve("result.txt"), report, StandardCharsets.UTF_8);
            mc.func_71400_g();
        }
    }

    private void fresh(int level, int power, int gems) throws Exception {
        if (mc.field_71462_r != null) mc.func_147108_a(null);
        network = new RecordingHandler(mc);
        WorldClient world = new WorldClient(network,
                new WorldSettings(0, WorldSettings.GameType.SURVIVAL, false, false, WorldType.field_77138_c),
                0, EnumDifficulty.PEACEFUL, mc.field_71424_I);
        player = new EntityClientPlayerMP(mc, world, mc.func_110432_I(), network, new StatFileWriter());
        mc.field_71441_e = world; mc.field_71439_g = player;
        mc.field_71442_b = new PlayerControllerMP(mc, network);
        data = MMM.getEntityNBT(player);
        check(data != null && data.carrer != null, "actual player NBT initialized");
        data.carrer.setLV(level);
        data.carrer.SpiritualPowerCount = power;
        data.carrer.SpiritualPower = new int[]{1,1,1,1,1,1,1,1};
        data.carrer.SpiritualPowerData = new int[]{1,2,3,4,5,6,7,8};
        data.carrer.setDayResource(DayResource.SpiritualPower, 0);
        data.carrer.LockSpiritualPower = "FFFFFFFF";
        player.field_71071_by.field_70462_a[0] = gems == 0 ? null : new ItemStack(ItemCraft2.ItemUltimateSoulGem, gems);
        gui = new GuiSpiritualPower(); mc.func_147108_a(gui);
        click(target(6));
        network.resets.clear();
    }

    @SuppressWarnings("unchecked") private List<GuiButton> buttons() throws Exception {
        return (List<GuiButton>) buttonList.get(gui);
    }
    private GuiButton auto(int stage) throws Exception {
        for (GuiButton b : buttons()) if (b.field_146127_k == 27840 + stage) return b;
        throw new AssertionError("Missing auto button " + stage);
    }
    private GuiButton target(int quality) throws Exception {
        for (GuiButton b : buttons()) if (b.field_146127_k == 27850 + quality) return b;
        throw new AssertionError("Missing target button " + quality);
    }
    private void click(GuiButton button) {
        gui.func_73864_a(button.field_146128_h + 2, button.field_146129_i + 2, 0);
    }
    private void step(int ticks) {
        for (int i = 0; i < ticks; i++) {
            if (mc.field_71462_r instanceof GuiSpiritualPower) mc.field_71462_r.func_73876_c();
            FMLCommonHandler.instance().bus().post(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        }
    }
    private void reply(int stage, int quality, boolean inventory) {
        data.carrer.SpiritualPowerCount -= SpiritualPower.needPower(stage, data);
        data.carrer.setDayResource(DayResource.SpiritualPower, Math.min(100,
                data.carrer.getDayResource(DayResource.SpiritualPower) + 1));
        data.carrer.SpiritualPower = data.carrer.SpiritualPower.clone();
        data.carrer.SpiritualPower[stage] = quality;
        if (inventory) {
            ItemStack stack = player.field_71071_by.field_70462_a[0];
            if (stack != null && --stack.field_77994_a <= 0) player.field_71071_by.field_70462_a[0] = null;
        }
    }
    private void render(String filename, int hoverStage) throws Exception {
        mc.func_147110_a().func_147610_a(true);
        GL11.glClearColor(.09F, .105F, .13F, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity();
        GL11.glOrtho(0, gui.field_146294_l, gui.field_146295_m, 0, 1000, 3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity(); GL11.glTranslatef(0, 0, -2000);
        GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glEnable(GL11.GL_TEXTURE_2D);
        int x = hoverStage < 0 ? 0 : auto(hoverStage).field_146128_h + 5;
        int y = hoverStage < 0 ? 0 : auto(hoverStage).field_146129_i + 5;
        gui.func_73863_a(x, y, 0);
        MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.DrawScreenEvent.Post(gui, x, y, 0));
        ScreenShotHelper.func_148259_a(fixture.toFile(), filename, mc.field_71443_c, mc.field_71440_d, mc.func_147110_a());
    }
    private void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }

    public static final class RecordingHandler extends NetHandlerPlayClient {
        final List<Integer> resets = new ArrayList<Integer>();
        RecordingHandler(Minecraft mc) { super(mc, null, local.foxfoxvalidation.MergedIdentity.offlineNetwork()); }
        @Override public void func_147297_a(Packet packet) {
            if (packet instanceof FMLProxyPacket) {
                ByteBuf buf = ((FMLProxyPacket)packet).payload().duplicate();
                if (buf.readableBytes() != 17) return;
                buf.readByte();
                int stage = buf.readInt(), count = buf.readInt();
                if (stage >= 0 && stage < 8 && count == 8) resets.add(stage);
            }
        }
    }
}
