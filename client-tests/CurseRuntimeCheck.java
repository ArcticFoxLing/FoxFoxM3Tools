package local.manametalcursetest;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.NetworkModHolder;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.*;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.util.*;
import net.minecraft.world.*;
import org.lwjgl.opengl.GL11;
import project.studio.manametalmod.client.*;

/** Isolated real Forge/GuiCurse probe. Never included in the release JAR. */
@Mod(modid="curseautotest", name="Isolated Curse Auto Check", version="1",
        dependencies="required-after:manametalmod;required-after:foxfoxm3tools")
public final class CurseRuntimeCheck {
    private boolean done;
    private int checks, closes;
    private Minecraft mc;
    private Path fixture;
    private GuiScreenBase gui;
    private final List<String> report = new ArrayList<String>();
    private final Field buttons = ReflectionHelper.findField(GuiScreen.class, "field_146292_n", "buttonList");
    private final Method mouse = ReflectionHelper.findMethod(GuiScreen.class, null,
            new String[]{"func_73864_a", "mouseClicked"}, int.class, int.class, int.class);

    @Mod.EventHandler public void init(FMLInitializationEvent event) { FMLCommonHandler.instance().bus().register(this); }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) throws Exception {
        mc = Minecraft.func_71410_x();
        if (done || event.phase != TickEvent.Phase.END || !(mc.field_71462_r instanceof GuiMainMenu)) return;
        done = true;
        fixture = Paths.get(System.getProperty("curseauto.test.root"));
        if (!mc.field_71412_D.getCanonicalFile().equals(fixture.resolve("client").toFile().getCanonicalFile()))
            throw new IllegalStateException("Not an isolated game directory");
        try {
            muted();
            local.foxfoxvalidation.MergedIdentity.verify();
            identity();
            fresh();
            layoutAndControls();
            persistenceAndManual();
            lifecycle();
            randomized();
            invalidData();
            muted();
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

    private void muted() {
        for (SoundCategory category : SoundCategory.values())
            check(mc.field_71474_y.func_151438_a(category) == 0F, "muted " + category.name());
    }

    private void identity() {
        ModContainer mod = Loader.instance().getIndexedModList().get("foxfoxm3tools");
        check(mod != null && "狐狐魔金小工具".equals(mod.getName()), "merged mod identity");
        NetworkModHolder holder = NetworkRegistry.INSTANCE.registry().get(mod);
        check(holder.check(Collections.<String,String>emptyMap(), Side.SERVER), "server does not need addon");
        check("自动：关".equals(StatCollector.func_74838_a("curseauto.off")), "Chinese language resource");
        report.add("PASS: release JAR loaded by Forge, client-only handshake, Chinese labels, all sound categories zero");
    }

    private void fresh() {
        NetworkManager manager = new NetworkManager(true) {
            @Override public java.net.SocketAddress func_74430_c() { return new java.net.InetSocketAddress("127.0.0.1", 0); }
            @Override public void func_150725_a(Packet packet, io.netty.util.concurrent.GenericFutureListener... listeners) { }
        };
        NetHandlerPlayClient network = new NetHandlerPlayClient(mc, null, manager) {
            @Override public void func_147297_a(Packet packet) {
                if (packet instanceof C0DPacketCloseWindow) closes++;
            }
        };
        WorldClient world = new WorldClient(network, new WorldSettings(0, WorldSettings.GameType.SURVIVAL,
                false, false, WorldType.field_77138_c), 0, EnumDifficulty.PEACEFUL, mc.field_71424_I);
        mc.field_71441_e = world;
        mc.field_71439_g = new EntityClientPlayerMP(mc, world, mc.func_110432_I(), network, new StatFileWriter());
        mc.field_71442_b = new PlayerControllerMP(mc, network);
        check(!mc.field_71439_g.field_71075_bZ.field_75098_d, "survival mode, no creative shortcut");
    }

    private void show(int type, long seed) throws Exception {
        gui = type == 1 ? new GuiCurse1() : new GuiCurse2();
        mc.func_147108_a(gui);
        Random random = new Random(seed);
        int[] board = board();
        for (int i=0; i<27; i++) board[i] = random.nextInt(type == 1 ? 4 : 6);
        if (type == 2) for (int i=0; i<27; i++) target()[i] = random.nextInt(6);
        closes = 0;
    }

    private void layoutAndControls() throws Exception {
        for (int type=1; type<=2; type++) {
            show(type, 4);
            check(list().size() == 2 && !enabled(), "exactly one extra button, initially off");
            int[] initial = board().clone();
            step(4);
            check(Arrays.equals(initial, board()) && closes == 0, "off mode leaves tiles untouched");
            for (int[] size : new int[][]{{320,240},{427,240},{640,360},{854,480},{320,240},{640,360}}) {
                gui.func_146280_a(mc, size[0], size[1]);
                GuiButton b = auto();
                check(list().size() == 2, "no duplicate after resize");
                check(b.field_146128_h >= 0 && b.field_146129_i >= 0
                        && b.field_146128_h+b.field_146120_f <= size[0]
                        && b.field_146129_i+b.field_146121_g <= size[1], "button fits window");
                check(!overlap(b,gui.guiLeft,gui.guiTop,gui.xSize,gui.ySize), "button does not cover puzzle");
                check(mc.field_71466_p.func_78256_a(b.field_146126_j) <= b.field_146120_f-4, "label fits button");
                if (size[0] == 320) render("curse"+type+"-small.png");
            }
            render("curse"+type+"-off.png");
            click(auto());
            step(1);
            check(enabled() && closes == 0, "mouse toggle starts without early confirmation");
            int changes = 0;
            for (int i=0; i<27; i++) if(initial[i] != board()[i]) changes++;
            check(changes == 1, "exactly one tile handled per tick");
            render("curse"+type+"-on.png");
            int[] partial = board().clone();
            gui.func_146280_a(mc,320,240);
            check(enabled() && Arrays.equals(partial,board()), "resize preserves mode and puzzle");
            click(auto()); step(6);
            check(!enabled() && Arrays.equals(partial,board()) && closes==0, "manual stop halts immediately");
            click(auto());
            solve();
            check(closes==1, "one original close packet after completion");
            step(5);
            check(closes==1, "no duplicate completion");
            fresh();
        }
        report.add("PASS: both real curse GUIs; toggle, stop/resume, resize at four sizes, compact labels, no overlap");
    }

    private void persistenceAndManual() throws Exception {
        show(1,12); click(auto()); solve();
        show(2,13);
        check(enabled(), "next curse remembers enabled state");
        solve();
        show(1,14); click(auto());
        show(2,15);
        check(!enabled(), "manual off remembered for following curses");
        // The original confirmation must reject an incorrect survival-mode puzzle.
        board()[0] = (target()[0]+1)%6;
        click(original());
        check(mc.field_71462_r==gui && closes==0, "manual confirmation rejects wrong puzzle");
        int before=board()[0];
        mouse.invoke(gui,gui.guiLeft+19,gui.guiTop+95,0);
        check(board()[0]==(before+1)%6 && !enabled(), "manual tile input remains functional");
        // Fully manual solution via the original mouse handler, then its button.
        for(int i=0;i<27;i++) while(board()[i]!=target()[i])
            mouse.invoke(gui,gui.guiLeft+19+(i%9)*24,gui.guiTop+95+(i/9)*24,0);
        click(original());
        check(mc.field_71462_r==null && closes==1, "original manual completion unchanged");
        report.add("PASS: automatic next curse, remembered manual off, original manual tiles and confirmation");
    }

    private void lifecycle() throws Exception {
        show(1,25); click(auto());
        int[] saved = board().clone();
        mc.func_147108_a(new GuiInventory(mc.field_71439_g));
        for(GuiButton b:list()) check(b.field_146127_k!=27965,"unrelated inventory has no auto button");
        step(2);
        check(Arrays.equals(saved,board())&&closes==0,"closed screen no longer receives input");
        show(2,26); check(enabled(),"ordinary screen change keeps preference");
        saved=board().clone();
        fresh(); step(1);
        check(Arrays.equals(saved,board())&&closes==0,"player/world change discards stale puzzle");
        show(1,27); check(!enabled(),"new world resets to off");
        click(auto());
        mc.field_71439_g=null; mc.field_71441_e=null; step(1);
        fresh(); show(2,28);
        check(!enabled(),"disconnect resets to off");
        report.add("PASS: unrelated GUI unaffected, closed puzzle discarded, new world and disconnect reset auto");
    }

    private void randomized() throws Exception {
        int rounds=0;
        for(int type=1;type<=2;type++) for(int seed=0;seed<100;seed++) {
            show(type,seed);
            if(!enabled())click(auto());
            int[] goal=target()==null?new int[27]:target().clone();
            int lifetime=integer("life");
            int ticks=solve();
            check(Arrays.equals(board(),goal),"seeded puzzle solved exactly");
            check(target()==null||Arrays.equals(target(),goal),"reference symbols never changed");
            check(integer("life")==lifetime-2*ticks,"original timer progresses without alteration");
            check(closes==1,"one close per seeded puzzle");
            rounds++;
        }
        // Exercise every source/target combination, including already solved boards.
        for(int type=1;type<=2;type++) {
            int n=type==1?4:6;
            for(int from=0;from<n;from++) for(int to=0;to<(type==1?1:6);to++) {
                show(type,0); Arrays.fill(board(),from); if(target()!=null)Arrays.fill(target(),to);
                if(!enabled())click(auto());
                int ticks=solve();
                check(ticks==(from==to?1:28),"uniform board worst case and already solved timing");
                for(int value:board())check(value==to,"uniform target reached");
                rounds++;
            }
        }
        report.add("PASS: "+rounds+" complete boards, including all symbol transitions and pre-solved boards; <=28 ticks; original timers and targets preserved");
    }

    private void invalidData() throws Exception {
        show(2,17); if(!enabled())click(auto());
        board()[0]=-1; step(1);
        check(!auto().field_146124_l && closes==0 && mc.field_71462_r==gui,"invalid symbol disables auto without closing");
        show(1,18);
        check(!enabled(),"incompatibility turns auto off");
        field("core").set(gui,new int[26]);click(auto());step(1);
        check(!auto().field_146124_l&&closes==0,"bad board length disables safely");
        show(2,19);click(auto());solve();
        report.add("PASS: malformed symbols/length stop safely, manual GUI retained, later compatible puzzle can run again");
    }

    private int solve() throws Exception {
        int ticks=0;
        while(mc.field_71462_r==gui && ticks<30) { step(1);ticks++; }
        check(mc.field_71462_r==null,"original confirmation closes solved GUI");
        check(gui instanceof GuiCurse1?((GuiCurse1)gui).testthis():((GuiCurse2)gui).testthis(),"original success predicate passes");
        check(ticks<=28,"finishes within 28 ticks");
        return ticks;
    }
    private void step(int ticks) {
        for(int i=0;i<ticks;i++) {
            muted();
            FMLCommonHandler.instance().bus().post(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
            if(mc.field_71462_r!=null)mc.field_71462_r.func_73876_c();
            FMLCommonHandler.instance().bus().post(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        }
    }
    private Field field(String name)throws Exception { Field f=gui.getClass().getDeclaredField(name);f.setAccessible(true);return f; }
    private int[] board()throws Exception{return (int[])field("core").get(gui);}
    private int[] target()throws Exception{return gui instanceof GuiCurse2?(int[])field("core2").get(gui):null;}
    private int integer(String name)throws Exception{return field(name).getInt(gui);}
    @SuppressWarnings("unchecked") private List<GuiButton> list()throws Exception{return (List<GuiButton>)buttons.get(mc.field_71462_r);}
    private GuiButton auto()throws Exception{for(GuiButton b:list())if(b.field_146127_k==27965)return b;throw new AssertionError("Missing auto button");}
    private GuiButton original()throws Exception{for(GuiButton b:list())if(b.field_146127_k==0)return b;throw new AssertionError("Missing confirm button");}
    private boolean enabled()throws Exception{return auto().field_146126_j.contains("\u00a7a");}
    private void click(GuiButton b)throws Exception{mouse.invoke(mc.field_71462_r,b.field_146128_h+2,b.field_146129_i+2,0);}
    private boolean overlap(GuiButton b,int x,int y,int w,int h){return b.field_146128_h<x+w&&b.field_146128_h+b.field_146120_f>x&&b.field_146129_i<y+h&&b.field_146129_i+b.field_146121_g>y;}
    private void render(String filename) {
        mc.func_147110_a().func_147610_a(true);
        GL11.glClearColor(.09F,.105F,.13F,1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0,gui.field_146294_l,gui.field_146295_m,0,1000,3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity(); GL11.glTranslatef(0,0,-2000);
        GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glEnable(GL11.GL_TEXTURE_2D);
        gui.func_73863_a(0,0,0);
        ScreenShotHelper.func_148259_a(fixture.toFile(),filename,mc.field_71443_c,mc.field_71440_d,mc.func_147110_a());
    }
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
}
