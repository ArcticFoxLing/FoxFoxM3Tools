package local.manametallockpicktest;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.*;
import cpw.mods.fml.common.network.internal.*;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.*;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.util.*;
import net.minecraft.world.*;
import org.lwjgl.opengl.GL11;
import project.studio.manametalmod.instance_dungeon.GuiGameUnlock;

/** Loads the release JAR in Forge. Uses the real GUI, rules, event buses and codec. */
@Mod(modid="lockpickautotest", name="Isolated Lockpick Auto Test", version="1",
        dependencies="required-after:manametalmod;required-after:foxfoxm3tools")
public final class LockpickRuntimeCheck {
    private boolean done;
    private int checks;
    private Minecraft mc;
    private Path fixture;
    private GuiGameUnlock gui;
    private final List<String> report = new ArrayList<String>();
    private final List<Integer> packets = new ArrayList<Integer>();
    private final Field buttons = ReflectionHelper.findField(GuiScreen.class, "field_146292_n", "buttonList");
    private final Method mouse = ReflectionHelper.findMethod(GuiScreen.class, null,
            new String[]{"func_73864_a", "mouseClicked"}, int.class, int.class, int.class);

    @Mod.EventHandler public void init(FMLInitializationEvent event) { FMLCommonHandler.instance().bus().register(this); }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) throws Exception {
        mc = Minecraft.func_71410_x();
        if (done || event.phase != TickEvent.Phase.END || !(mc.field_71462_r instanceof GuiMainMenu)) return;
        done = true;
        fixture = Paths.get(System.getProperty("lockpickauto.test.root"));
        if (!mc.field_71412_D.getCanonicalFile().equals(fixture.resolve("client").toFile().getCanonicalFile()))
            throw new IllegalStateException("Not an isolated game directory");
        try {
            identity();
            for (String name : NetworkRegistry.INSTANCE.channelNamesFor(Side.CLIENT)) {
                FMLEmbeddedChannel channel = NetworkRegistry.INSTANCE.getChannel(name, Side.CLIENT);
                if (channel.pipeline().get(FMLOutboundHandler.class) != null)
                    channel.pipeline().replace(FMLOutboundHandler.class, "test-transport", new ChannelOutboundHandlerAdapter() {
                        @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
                            if (message instanceof FMLProxyPacket) {
                                ByteBuf buf = ((FMLProxyPacket)message).payload().duplicate();
                                if (buf.readableBytes() == 17 && buf.readUnsignedByte() == 73) {
                                    check(buf.readInt() == 12 && buf.readInt() == 64 && buf.readInt() == 34,
                                            "original completion packet retains chest coordinates");
                                    packets.add(buf.readInt());
                                }
                            }
                            promise.setSuccess();
                        }
                    });
            }
            fresh();
            layoutAndControls();
            boundariesAndMovingTarget();
            sweptTarget();
            lifecycle();
            randomizedRounds();
            report.add("checks=" + checks);
            report.add("status=PASS");
        } catch (Throwable error) {
            StringWriter trace = new StringWriter(); error.printStackTrace(new PrintWriter(trace));
            report.add(trace.toString()); report.add("status=FAIL");
        } finally {
            mc.field_71462_r = null; mc.field_71439_g = null; mc.field_71441_e = null; mc.field_71442_b = null;
            mc.func_147108_a(new GuiMainMenu());
            Files.write(fixture.resolve("result.txt"), report, StandardCharsets.UTF_8);
            mc.func_71400_g();
        }
    }

    private void identity() throws Exception {
        ModContainer mod = Loader.instance().getIndexedModList().get("foxfoxm3tools");
        check(mod != null && "狐狐魔金小工具".equals(mod.getName()), "merged Chinese mod identity");
        local.foxfoxvalidation.MergedIdentity.verify();
        check(!Loader.isModLoaded("manametallockpickauto"), "standalone lockpick mod absent");
        NetworkModHolder holder = NetworkRegistry.INSTANCE.registry().get(mod);
        check(holder.check(Collections.<String,String>emptyMap(), Side.SERVER), "server does not need this addon");
        check("自动：关".equals(StatCollector.func_74838_a("lockpickauto.off")), "Chinese language resource");
        report.add("PASS: merged FoxFoxM3Tools entry, Chinese labels, client-only handshake; standalone lockpick mod absent");
    }

    private void fresh() {
        NetworkManager manager = new NetworkManager(true) {
            @Override public java.net.SocketAddress func_74430_c() { return new java.net.InetSocketAddress("127.0.0.1", 0); }
            @Override public void func_150725_a(Packet packet, io.netty.util.concurrent.GenericFutureListener... listeners) { }
        };
        NetHandlerPlayClient network = new NetHandlerPlayClient(mc, null, manager) {
            @Override public void func_147297_a(Packet packet) { }
        };
        WorldClient world = new WorldClient(network, new WorldSettings(0, WorldSettings.GameType.SURVIVAL,
                false, false, WorldType.field_77138_c), 0, EnumDifficulty.PEACEFUL, mc.field_71424_I);
        mc.field_71441_e = world;
        mc.field_71439_g = new EntityClientPlayerMP(mc, world, mc.func_110432_I(), network, new StatFileWriter());
        mc.field_71442_b = new PlayerControllerMP(mc, network);
    }

    private void show(int difficulty, int seconds, int risk, long seed) throws Exception {
        gui = new GuiGameUnlock(difficulty, seconds, risk, 12, 64, 34, true);
        set("rand", new Random(seed));
        mc.func_147108_a(gui);
        packets.clear();
    }

    private void layoutAndControls() throws Exception {
        show(800, 15, 30, 41);
        check(list().size() == 2 && !enabled(), "one button, off by default");
        step(5);
        check(!bool("start") && number("nowdata") == 0 && packets.isEmpty(), "off mode never starts");
        for (int[] size : new int[][]{{320,240},{427,240},{640,360},{854,480},{320,240},{640,360}}) {
            gui.func_146280_a(mc, size[0], size[1]);
            check(list().size() == 2, "resizing never duplicates the button");
            GuiButton b = auto();
            check(b.field_146128_h >= 0 && b.field_146129_i >= 0
                    && b.field_146128_h + b.field_146120_f <= size[0]
                    && b.field_146129_i + b.field_146121_g <= size[1], "button stays on screen");
            check(!overlap(b, gui.guiLeft, gui.guiTop, gui.xSize, gui.ySize), "button leaves original panel unobstructed");
            if (size[0] == 320) render("lockpick-small.png");
        }
        render("lockpick-off.png");
        click(auto()); step(1);
        check(enabled() && bool("start") && packets.isEmpty(), "enabling invokes original start, no unlock packet");
        step(12); render("lockpick-on.png");
        float progress = number("nowdata");
        gui.func_146280_a(mc, 320, 240);
        check(enabled() && number("nowdata") == progress && bool("start"), "resize preserves auto and original progress");
        gui.func_146280_a(mc, 640, 360);
        click(auto());
        arrow(80, 10, 76f, 0f);
        endTick();
        check(!enabled() && bool("hasArrow"), "off returns control without playing or resetting round");
        click(auto()); endTick();
        check(enabled() && !bool("fail"), "can resume the current round");
        report.add("PASS: off by default, real mouse toggle, automatic original start, resize at four sizes, no overlap, manual stop/resume");
    }

    private void boundariesAndMovingTarget() throws Exception {
        show(400, 20, 30, 12); click(auto()); endTick();
        // Use precise states to test the real original input handler's boundaries.
        float progress = number("nowdata");
        arrow(80, 10, 75.99f, 0f); endTick();
        check(bool("hasArrow") && !bool("fail"), "integer centre before target must wait");
        arrow(80, 10, 76f, 0f); endTick();
        check(!bool("hasArrow") && !bool("fail"), "left endpoint is legal");
        arrow(80, 10, 86.99f, 0f); endTick();
        check(!bool("hasArrow") && !bool("fail"), "right endpoint uses truncation, not rounding");
        arrow(80, 10, 87f, 0f); endTick();
        check(bool("hasArrow") && !bool("fail"), "outside right endpoint must not press");
        set("hasArrow", false); endTick();
        check(!bool("fail"), "never presses without an arrow");
        check(number("nowdata") == progress && packets.isEmpty(), "automation never alters progress or directly unlocks");

        show(900, 20, 30, 13); click(auto()); endTick();
        set("rand", new Random(10) {
            private boolean first = true;
            @Override public boolean nextBoolean() { boolean result = first; first = false; return result; }
        });
        arrow(80, 10, 76f, 8f); endTick();
        check(bool("hasArrow") && integer("yellowPos") >= 120
                && Math.abs(number("arrowSpeed") - 1.6f) < .001f, "real high-difficulty target shift and slowdown");
        int target = integer("yellowPos"); endTick();
        check(bool("hasArrow") && integer("yellowPos") == target && !bool("fail"), "wait after target moves");
        for (int i = 0; i < 150 && bool("hasArrow"); i++) step(1);
        check(!bool("hasArrow") && !bool("fail"), "follows moved target and presses again");
        report.add("PASS: exact integer boundaries, no-arrow waiting, shifted target and second input; timer/progress left to original GUI");
    }

    private void lifecycle() throws Exception {
        show(100, 5, 30, 17); click(auto()); endTick();
        mc.func_147108_a(null); endTick();
        check(packets.isEmpty(), "closing does not submit a result");
        show(100, 5, 30, 17);
        check(!enabled(), "next chest defaults off");
        // Original start button must still work without enabling auto.
        click(original());
        check(bool("start") && !enabled(), "original start remains usable");
        click(auto()); step(160);
        check(bool("success") && !bool("fail") && !enabled() && !auto().field_146124_l,
                "completion stops auto and disables it");
        check(packets.equals(Arrays.asList(1)), "original GUI sends exactly one success packet");
        step(50);
        check(packets.equals(Arrays.asList(1)), "no duplicate completion or restart");
        render("lockpick-success.png");

        show(400, 20, 30, 18); click(auto()); endTick();
        gui.fail(); endTick();
        check(!enabled() && !auto().field_146124_l && packets.equals(Arrays.asList(2)),
                "original failure stops auto and retains normal failure packet");
        step(10);
        check(packets.size() == 1, "no retry after failure");

        show(400, 20, 30, 19); click(auto()); endTick();
        arrow(80, 10, 76f, 0f);
        fresh(); endTick();
        check(bool("hasArrow") && packets.isEmpty(), "player/world replacement discards session");
        mc.func_147108_a(new GuiInventory(mc.field_71439_g));
        for (GuiButton b : list()) check(b.field_146127_k != 27964, "other screens do not get the button");
        report.add("PASS: original manual start, close/reopen, success/failure stop, exactly one original result packet, world change, unrelated screen");
    }

    private void sweptTarget() throws Exception {
        show(1000, 20, 30, 23); click(auto()); endTick();
        set("rand", new Random(20) { @Override public boolean nextBoolean() { return false; } });
        arrow(80, 10, 75.99f, 12.8f);
        float progress = number("nowdata"), increment = number("add");
        step(1);
        check(!bool("hasArrow") && !bool("fail"), "swept target triggers original clearArrow");
        check(number("arrowPos") == 0 && number("nowdata") == progress + increment && packets.isEmpty(),
                "crossing leaves original reset and elapsed time intact, no completion packet");
        endTick(); check(!bool("fail"), "snapshot consumed exactly once");

        set("rand", new Random(20) { @Override public boolean nextBoolean() { return true; } });
        arrow(80, 10, 75.99f, 12.8f);
        step(1);
        check(bool("hasArrow") && integer("yellowPos") >= 120
                && Math.abs(number("arrowSpeed") - 2.56f) < .001f
                && Math.abs(number("arrowPos") - 88.79f) < .001f,
                "crossing preserves target shift, original slowdown and completed movement");
        // If manual input moves the target between START and END, discard the sample.
        arrow(80, 10, 75.99f, 12.8f);
        FMLCommonHandler.instance().bus().post(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
        set("yellowPos", 81); set("arrowPos", 92f); endTick();
        check(number("arrowPos") == 92f && integer("yellowPos") == 81 && !bool("fail"),
                "stale sample after target change is not used");
        arrow(80, 10, 75.99f, 12.8f);
        FMLCommonHandler.instance().bus().post(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
        click(auto()); set("arrowPos", 88.79f); endTick();
        check(bool("hasArrow") && number("arrowPos") == 88.79f, "stopping discards pending crossing");
        click(auto()); endTick();
        check(bool("hasArrow") && !bool("fail"), "reenabling never retroactively presses for a missed arrow");
        report.add("PASS: high-speed crossing compensation, original clear/shift/slowdown, unchanged timer; stale or stopped samples discarded");
    }

    private void randomizedRounds() throws Exception {
        int total = 0, compensated = 0;
        int[] difficulties = {100,200,300,400,500,600,700,800,900,950,1000};
        for (int difficulty : difficulties) {
            int successful = 0, compensatedRounds = 0;
            for (int seed = 0; seed < 40; seed++) {
                show(difficulty, 20, 30, seed);
                click(auto()); endTick();
                boolean skippedWindow = false;
                for (int tick = 0; tick < 450 && !bool("success") && !bool("fail"); tick++) {
                    if (bool("hasArrow")) {
                        int centre = (int)number("arrowPos") + 4;
                        int next = (int)(number("arrowPos") + number("arrowSpeed")) + 4;
                        if (centre < integer("yellowPos") && next > integer("yellowPos") + integer("yellowWidth"))
                            skippedWindow = true;
                    }
                    step(1);
                }
                check(bool("success") && !bool("fail"), "every complete seeded round must succeed");
                successful++;
                if (skippedWindow) { compensatedRounds++; compensated++; }
                check(!enabled() && packets.size() == 1 && packets.get(0) == (bool("success") ? 1 : 2),
                        "each randomized round ends once using the original result path");
                total++;
            }
            report.add("ROUNDS difficulty=" + difficulty + " success=" + successful + "/40 compensatedRounds=" + compensatedRounds);
            check(successful == 40, "all difficulties, including 1000, succeed");
        }
        check(compensated > 0, "randomized suite actually exercises crossing compensation");
        report.add("PASS: " + total + " complete seeded rounds; rounds requiring crossing compensation=" + compensated);
    }

    private void arrow(int left, int width, float position, float speed) throws Exception {
        set("yellowPos", left); set("yellowWidth", width); set("arrowPos", position);
        set("arrowSpeed", speed); set("hasArrow", true);
    }
    private void step(int ticks) {
        for (int i=0; i<ticks; i++) {
            FMLCommonHandler.instance().bus().post(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
            if (mc.field_71462_r != null) mc.field_71462_r.func_73876_c();
            endTick();
        }
    }
    private void endTick() { FMLCommonHandler.instance().bus().post(new TickEvent.ClientTickEvent(TickEvent.Phase.END)); }
    private Field field(String name) throws Exception { Field f=GuiGameUnlock.class.getDeclaredField(name); f.setAccessible(true); return f; }
    private void set(String name,Object value) throws Exception { field(name).set(gui,value); }
    private boolean bool(String name) throws Exception { return field(name).getBoolean(gui); }
    private float number(String name) throws Exception { return field(name).getFloat(gui); }
    private int integer(String name) throws Exception { return field(name).getInt(gui); }
    @SuppressWarnings("unchecked") private List<GuiButton> list() throws Exception { return (List<GuiButton>)buttons.get(mc.field_71462_r); }
    private GuiButton auto() throws Exception { for (GuiButton b:list()) if(b.field_146127_k==27964) return b; throw new AssertionError("Missing auto button"); }
    private GuiButton original() throws Exception { for(GuiButton b:list()) if(b.field_146127_k==0) return b; throw new AssertionError("Missing original button"); }
    private boolean enabled() throws Exception { return auto().field_146126_j.contains("\u00a7a"); }
    private void click(GuiButton b) throws Exception { mouse.invoke(mc.field_71462_r,b.field_146128_h+2,b.field_146129_i+2,0); }
    private boolean overlap(GuiButton b,int x,int y,int w,int h) {
        return b.field_146128_h<x+w && b.field_146128_h+b.field_146120_f>x
                && b.field_146129_i<y+h && b.field_146129_i+b.field_146121_g>y;
    }
    private void render(String filename) {
        mc.func_147110_a().func_147610_a(true);
        GL11.glClearColor(.09F,.105F,.13F,1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0,gui.field_146294_l,gui.field_146295_m,0,1000,3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity(); GL11.glTranslatef(0,0,-2000);
        GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glEnable(GL11.GL_TEXTURE_2D);
        gui.func_73863_a(0,0,0);
        ScreenShotHelper.func_148259_a(fixture.toFile(),filename,mc.field_71443_c,mc.field_71440_d,mc.func_147110_a());
    }
    private void check(boolean condition,String message) { checks++; if(!condition) throw new AssertionError(message); }
}
