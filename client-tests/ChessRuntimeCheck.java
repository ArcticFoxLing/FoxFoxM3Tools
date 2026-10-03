package local.manametalchesstest;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.relauncher.Side;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.*;
import local.foxfoxm3tools.chess.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.*;
import net.minecraftforge.client.ClientCommandHandler;
import project.studio.manametalmod.chess.*;
import org.lwjgl.input.Keyboard;

/** Test-only mod, never packaged in the release. Uses an isolated integrated server. */
@Mod(modid="chessautotest",name="Isolated Chess Runtime Check",version="1",
        dependencies="required-after:manametalmod;required-after:foxfoxm3tools")
public final class ChessRuntimeCheck {
    private boolean started,done,playing;
    private volatile boolean seeded;
    private volatile Throwable serverError;
    private volatile int blackKing=1,whiteKing=1;
    private int serverTicks,clientTicks,checks,phase;
    private Path root;
    private long deadline;
    private ClientHooks hooks;
    private String previous="";
    private final List<String> report=new ArrayList<String>();
    private volatile TileEntityChessAI tile;
    @Mod.EventHandler public void init(FMLInitializationEvent e) { FMLCommonHandler.instance().bus().register(this); }
    private void check(boolean ok,String what) { checks++;if(!ok) throw new AssertionError(what); }
    private void log(String line) throws IOException {
        report.add(line);Files.write(root.resolve("progress.txt"),report,StandardCharsets.UTF_8);
    }
    private void muted(Minecraft mc) {
        for(SoundCategory c:SoundCategory.values()) check(mc.field_71474_y.func_151438_a(c)==0F,"muted "+c.name());
    }
    @SubscribeEvent public void server(TickEvent.ServerTickEvent event) {
        if(!started||done||event.phase!=TickEvent.Phase.END) return;
        try {
            MinecraftServer server=MinecraftServer.func_71276_C();
            if(server==null||server.func_71203_ab()==null) return;
            EntityPlayerMP player=server.func_71203_ab().func_152612_a("FoxFoxTest");
            if(player==null) return;
            if(seeded) {
                // Observe block metadata without touching the AI's mutable list.
                whiteKing=blackKing=0;
                for(int x=-3;x<5;x++) for(int z=3;z<11;z++) if(player.field_70170_p.func_147439_a(x,6,z)==ChessCore.BlcokChess) {
                    int meta=player.field_70170_p.func_72805_g(x,6,z);
                    if(meta==4) whiteKing=1;if(meta==10) blackKing=1;
                }
                return;
            }
            if(++serverTicks<120) return;
            final WorldServer world=server.func_71218_a(0);
            for(int cx=-1;cx<=0;cx++) for(int cz=-1;cz<=0;cz++) world.func_72863_F().func_73158_c(cx,cz);
            for(int x=-8;x<=9;x++) for(int z=-5;z<=14;z++) {
                world.func_147465_d(x,5,z,Blocks.field_150348_b,0,3);
                for(int y=6;y<12;y++) world.func_147468_f(x,y,z);
            }
            if(player.field_71093_bK!=0) server.func_71203_ab().transferPlayerToDimension(player,0,new Teleporter(world) {
                @Override public void func_77185_a(net.minecraft.entity.Entity e,double x,double y,double z,float yaw) { e.func_70080_a(1,6,7,yaw,0); }
            });
            player.func_71033_a(WorldSettings.GameType.SURVIVAL);
            Arrays.fill(player.field_71071_by.field_70462_a,null);
            player.field_71071_by.field_70461_c=0;
            world.func_147465_d(0,6,0,ChessCore.BlcokChessAI,0,3);
            tile=(TileEntityChessAI)world.func_147438_o(0,6,0);
            tile.func_145845_h();tile.rightClick(player);
            player.func_70634_a(1,6,7);player.field_71069_bz.func_75142_b();
            for(int cx=-1;cx<=0;cx++) for(int cz=-1;cz<=0;cz++) player.field_71135_a.func_147359_a(
                    new net.minecraft.network.play.server.S21PacketChunkData(world.func_72964_e(cx,cz),true,65535));
            seeded=true;
        } catch(Throwable error) { serverError=error; }
    }
    @SubscribeEvent public void client(TickEvent.ClientTickEvent event) {
        if(done||event.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.func_71410_x();
        try {
            if(!started) {
                if(!(mc.field_71462_r instanceof GuiMainMenu)) return;
                root=Paths.get(System.getProperty("chessauto.test.root"));
                check(mc.field_71412_D.getCanonicalFile().equals(root.resolve("client").toFile().getCanonicalFile()),"isolated directory");
                muted(mc);
                local.foxfoxvalidation.MergedIdentity.verify();
                ModContainer mod=Loader.instance().getIndexedModList().get("foxfoxm3tools");
                check(mod!=null&&mod.getVersion().equals("1.10.1"),"release loaded by Forge");
                check(NetworkRegistry.INSTANCE.registry().get(mod).check(Collections.<String,String>emptyMap(),Side.SERVER),"client-only handshake");
                check(!ClientCommandHandler.instance.func_71555_a().containsKey("mmchess"),"chat command removed");
                Field field=cpw.mods.fml.common.eventhandler.EventBus.class.getDeclaredField("listeners");field.setAccessible(true);
                for(Object value:((Map<?,?>)field.get(FMLCommonHandler.instance().bus())).keySet())
                    if(value instanceof ClientHooks) { check(hooks==null,"one chess hook");hooks=(ClientHooks)value; }
                check(hooks!=null,"merged chess hook registered");
                check(hooks.toggle.func_151463_i()==Keyboard.KEY_F8,"F8 is the default binding");
                check(Arrays.asList(mc.field_71474_y.field_74324_K).contains(hooks.toggle),"binding is in Controls");
                verifyRules();
                log("PASS: Forge mod identity, client-only handshake, runtime mute, original-rule differential checks");
                started=true;deadline=System.currentTimeMillis()+780000;
                mc.field_71474_y.field_82881_y=false;
                mc.func_71371_a("chess-isolated","Auto chess isolated test",new WorldSettings(2103,WorldSettings.GameType.SURVIVAL,false,false,WorldType.field_77138_c));
                return;
            }
            if(serverError!=null) throw new AssertionError("Server fixture error",serverError);
            if(System.currentTimeMillis()>deadline) throw new AssertionError("Timeout: "+hooks.status());
            if(playing&&(mc.field_71439_g==null||mc.field_71441_e==null)) throw new AssertionError("Disconnected: "+hooks.status());
            if(!seeded||mc.field_71439_g==null||mc.field_71441_e==null) return;
            if(!playing) {
                ItemStack held=mc.field_71439_g.func_71045_bC();
                if(held==null||held.func_77973_b()!=ChessCore.itemchessstick||mc.field_71441_e.func_147439_a(-3,5,3)!=ChessCore.BlockChessboard
                        ||Math.abs(mc.field_71439_g.field_70165_t-1)>.2) return;
                if(mc.field_71462_r!=null) mc.func_147108_a(null);
                if(++clientTicks<40) return;
                lifecycle(mc);
                press();release();
                check(active(),"registered key binding starts session");
                playing=true;clientTicks=0;log("START: actual white automation against the original black AI, survival, original block interaction");
                return;
            }
            muted(mc);clientTicks++;
            String status=hooks.status();
            if(!status.equals(previous)) { log(status);previous=status; }
            if(Boolean.getBoolean("chessauto.test.controls")&&status.contains("已走 1 步")) {
                press();release();check(!active(),"F8 stops after real white and black moves");
                log("PASS: F8 started automation, real server accepted the white move, original AI replied, F8 stopped automation");
                finish(mc,null);return;
            }
            if(!active()) {
                check(status.contains("黑王已被吃掉"),"completed game: "+status);
                check(blackKing==0,"server confirms black king removed");
                check(whiteKing==1,"white king survives");
                log("PASS: full game completed via real client/server packets; original AI defeated");
                finish(mc,null);
            }
        } catch(Throwable error) { finish(mc,error); }
    }
    private boolean active() throws Exception { Field f=ClientHooks.class.getDeclaredField("session");f.setAccessible(true);return f.get(hooks)!=null; }
    private void press() {
        KeyBinding.func_74510_a(hooks.toggle.func_151463_i(),true);
        KeyBinding.func_74507_a(hooks.toggle.func_151463_i());
        hooks.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
    }
    private void release() {
        KeyBinding.func_74510_a(hooks.toggle.func_151463_i(),false);
        hooks.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
    }
    private void lifecycle(Minecraft mc) throws Exception {
        press();check(active(),"F8 starts fresh board");
        press();press();check(active(),"held key and repeat events do not toggle again");
        release();press();check(!active(),"second F8 press stops");release();
        mc.field_71462_r=new GuiChat();press();release();check(!active(),"typing in chat does not activate");
        mc.field_71462_r=null;release();check(!active(),"menu key press is drained");
        hooks.toggle.func_151462_b(-97);KeyBinding.func_74508_b();
        press();check(active(),"rebinding to mouse button starts");release();
        press();check(!active(),"mouse button stops");release();
        hooks.toggle.func_151462_b(0);KeyBinding.func_74508_b();
        press();release();check(!active(),"unbound key does not activate");
        hooks.toggle.func_151462_b(Keyboard.KEY_F8);KeyBinding.func_74508_b();
        press();release();mc.field_71439_g.field_71071_by.field_70461_c=1;
        hooks.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));check(!active(),"switching hotbar stops");
        mc.field_71439_g.field_71071_by.field_70461_c=0;
        double x=mc.field_71439_g.field_70165_t;mc.field_71439_g.field_70165_t=100;
        hooks.start();check(!active(),"out of reach does not start");mc.field_71439_g.field_70165_t=x;
        hooks.start();mc.field_71462_r=new GuiIngameMenu();hooks.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        check(!active(),"opening menu stops");mc.field_71462_r=null;
        log("PASS: F8 on/off, hold/repeat guard, chat/menu guard, mouse rebinding, unbound key, held-item cancellation, range guard");
    }
    private void verifyRules() throws Exception {
        Position p=Position.initial();Random random=new Random(903);
        int compared=0;
        for(int step=0;step<800;step++) {
            TileEntityChessAI original=new TileEntityChessAI();
            original.whiteKingMoved=(p.castles&3)==0;original.blackKingMoved=(p.castles&12)==0;
            original.whiteRightRookMoved=(p.castles&1)==0;original.whiteLeftRookMoved=(p.castles&2)==0;
            original.blackRightRookMoved=(p.castles&4)==0;original.blackLeftRookMoved=(p.castles&8)==0;
            original.hasEnPassant=p.ep>=0;
            if(p.ep>=0) {
                original.enPassantTargetX=p.ep%8;original.enPassantTargetZ=p.ep/8;
                original.enPassantPawnX=p.ep%8;original.enPassantPawnZ=p.ep/8-p.side;
                original.enPassantPawnBlack=p.side==1;
            }
            for(int s=0;s<64;s++) if(p.board[s]!=0) original.list.add(new ChessPos(s%8,s/8,ChessType.getType(Math.abs(p.board[s])-1),p.board[s]<0,0));
            Set<Integer> expected=new HashSet<Integer>();
            for(ChessPos piece:original.list) if(piece.isBlack==(p.side<0))
                for(int[] m:original.getValidMovess(piece)) expected.add(Position.move(piece.targetX+piece.targetZ*8,m[0]+m[1]*8));
            int[] moves=new int[256];int n=p.moves(moves);Set<Integer> actual=new HashSet<Integer>();
            for(int i=0;i<n;i++) actual.add(moves[i]);
            check(expected.equals(actual),"original pseudo-legal move parity at ply "+step);compared+=n;
            if(n==0||p.king(1)<0||p.king(-1)<0) p=Position.initial();else p=p.play(moves[random.nextInt(n)]);
        }
        log("PASS: "+compared+" generated moves compared to installed ManaMetal 8.0.7 across 800 positions");
    }
    private void finish(Minecraft mc,Throwable error) {
        done=true;
        try {
            if(hooks!=null&&active()) hooks.stop("测试结束");
            if(tile!=null&&tile.ai!=null) { tile.ai.bQuit=true;tile.ai.aiCaller.exit(); }
            if(error!=null) { StringWriter s=new StringWriter();error.printStackTrace(new PrintWriter(s));report.add(s.toString()); }
            report.add("checks="+checks);report.add(error==null?"status=PASS":"status=FAIL");
            Files.write(root.resolve("result.txt"),report,StandardCharsets.UTF_8);
        } catch(Exception ex) { ex.printStackTrace(); }
        mc.func_71400_g();
    }
}
