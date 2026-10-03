package local.manametalwatertest;

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
import local.foxfoxm3tools.water.*;
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
import project.studio.manametalmod.watergame.*;
import project.studio.manametalmod.instance_dungeon.*;
import project.studio.manametalmod.config.M3Config;
import project.studio.manametalmod.core.Pos;
import org.lwjgl.input.Keyboard;

/** Test fixture only. The release contains no server code or world setup. */
@Mod(modid="waterautotest",name="Isolated Water Runtime Check",version="1",
        dependencies="required-after:manametalmod;required-after:foxfoxm3tools")
public final class WaterRuntimeCheck {
    private volatile boolean started,done,seeded,requestSeed=true,flowConfirmed,rewardConfirmed,inventoryPrepared;
    private boolean playing,controlsChecked,resumeChecked;
    private volatile Throwable serverError;
    private volatile int round;
    private int serverTicks,clientTicks,checks,settle,giftTicks;
    private Path root;
    private long deadline;
    private ClientHooks hooks;
    private String previous="";
    private final List<String> report=new ArrayList<String>();
    private volatile TileEntityWaterGameCore core;
    private MobDunageonHeroTemple judge;
    private WaterGamePuzzleGenerator.Result generated;
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
                if(!inventoryPrepared && ++giftTicks>=30) {
                    // Original giveItems spawns pickup entities; wait for real pickup
                    // before moving gifts out of the hotbar for the inventory test.
                    int items=0;
                    for(ItemStack stack:player.field_71071_by.field_70462_a) if(stack!=null) items+=stack.field_77994_a;
                    if(items<generated.giveI+generated.giveL+generated.giveT+generated.giveX+generated.giveWaterDown)
                        throw new AssertionError("Fixture did not collect original puzzle gifts");
                    for(int i=0;i<9;i++) { player.field_71071_by.field_70462_a[i+9]=player.field_71071_by.field_70462_a[i];player.field_71071_by.field_70462_a[i]=null; }
                    if(player.field_70170_p.func_147439_a(-5,5,6)!=Blocks.field_150348_b)
                        throw new AssertionError("Fixture floor was overwritten");
                    player.func_70634_a(-4.5,6,6.5);
                    player.field_71069_bz.func_75142_b();inventoryPrepared=true;
                }
                net.minecraft.tileentity.TileEntity t=player.field_70170_p.func_147438_o(-3+generated.endX,6,3+generated.endZ);
                if(t instanceof TileEntityWaterGame && ((TileEntityWaterGame)t).hasWater) flowConfirmed=true;
                if(flowConfirmed && judge.field_70128_L) {
                    for(Object entity:player.field_70170_p.field_72996_f)
                        if(entity instanceof project.studio.manametalmod.mob.EntityItemHolyDevice)
                            for(ItemStack stack:((project.studio.manametalmod.mob.EntityItemHolyDevice)entity).item)
                                if(stack!=null && stack.func_77973_b()==InstanceDungeonCore.ItemHeroTestok && stack.func_77960_j()==4) rewardConfirmed=true;
                }
            }
            if(!requestSeed||++serverTicks<120) return;
            final WorldServer world=server.func_71218_a(0);
            if(player.field_71093_bK!=0) {
                server.func_71203_ab().transferPlayerToDimension(player,0,new Teleporter(world) {
                    @Override public void func_77185_a(net.minecraft.entity.Entity e,double x,double y,double z,float yaw) { e.func_70080_a(-4.5,6,6.5,yaw,0); }
                });
                // Let dimension/chunk synchronization settle before creating the fixture.
                serverTicks=60;return;
            }
            for(Object entity:new ArrayList<Object>(world.field_72996_f))
                if(entity instanceof project.studio.manametalmod.mob.EntityItemHolyDevice) ((net.minecraft.entity.Entity)entity).func_70106_y();
            for(int cx=-1;cx<=1;cx++) for(int cz=-1;cz<=1;cz++) world.func_72863_F().func_73158_c(cx,cz);
            world.func_82736_K().func_82764_b("doMobSpawning","false");
            for(int x=-10;x<=14;x++) for(int z=-6;z<=20;z++) {
                world.func_147465_d(x,5,z,Blocks.field_150348_b,0,3);
                for(int y=6;y<14;y++) world.func_147468_f(x,y,z);
            }
            player.func_71033_a(WorldSettings.GameType.SURVIVAL);
            Arrays.fill(player.field_71071_by.field_70462_a,null);
            player.field_71071_by.field_70461_c=0;
            player.func_70634_a(-4.5,6,6.5);
            world.func_147465_d(0,6,0,WaterGameCore.watergamecore,0,3);
            core=(TileEntityWaterGameCore)world.func_147438_o(0,6,0);
            long parentSeed=903+round*117;
            if(round==2) {
                // Include a missing waterfall, rather than leaving that input variant
                // to chance in a small end-to-end test sample.
                for(int attempt=0;attempt<64;attempt++,parentSeed++) {
                    WaterGamePuzzleGenerator.Result candidate=new Blueprint(new Random(parentSeed).nextLong()).puzzle;
                    if(candidate.missing[candidate.dropX][candidate.dropZ]) break;
                }
            }
            generated=WaterGamePuzzleGenerator.generate(world,0,6,0,player,new Random(parentSeed));
            if(round==2 && !generated.missing[generated.dropX][generated.dropZ]) throw new AssertionError("Missing waterfall fixture not found");
            core.puzzleSeed=generated.seed;core.puzzleId=(int)(generated.seed&Integer.MAX_VALUE);core.generated=true;
            world.func_147471_g(0,6,0);
            // Only this isolated process treats the flat fixture as the instance dimension.
            // This exercises the original trial judge, completion callback and reward.
            M3Config.WorldInstanceDungeonID=0;
            judge=new MobDunageonHeroTemple(world,IDungeonDifficult.EASY,5,0L,new Pos(0,6,0));
            judge.func_70080_a(0,8,0,0,0);world.func_72838_d(judge);
            player.func_70634_a(-4.5,6,6.5);player.field_71069_bz.func_75142_b();
            for(int cx=-1;cx<=1;cx++) for(int cz=-1;cz<=1;cz++) player.field_71135_a.func_147359_a(
                    new net.minecraft.network.play.server.S21PacketChunkData(world.func_72964_e(cx,cz),true,65535));
            requestSeed=false;flowConfirmed=false;rewardConfirmed=false;inventoryPrepared=false;giftTicks=0;seeded=true;
        } catch(Throwable error) { serverError=error; }
    }
    @SubscribeEvent public void client(TickEvent.ClientTickEvent event) {
        if(done||event.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.func_71410_x();
        try {
            if(!started) {
                if(!(mc.field_71462_r instanceof GuiMainMenu)) return;
                root=Paths.get(System.getProperty("waterauto.test.root"));
                check(mc.field_71412_D.getCanonicalFile().equals(root.resolve("client").toFile().getCanonicalFile()),"isolated directory");
                muted(mc);
                local.foxfoxvalidation.MergedIdentity.verify();
                ModContainer mod=Loader.instance().getIndexedModList().get("foxfoxm3tools");
                check(mod!=null&&mod.getVersion().equals("1.10.1"),"release loaded by Forge");
                check(NetworkRegistry.INSTANCE.registry().get(mod).check(Collections.<String,String>emptyMap(),Side.SERVER),"client-only handshake");
                check(!ClientCommandHandler.instance.func_71555_a().containsKey("mmwater"),"no water command registered");
                Field field=cpw.mods.fml.common.eventhandler.EventBus.class.getDeclaredField("listeners");
                field.setAccessible(true);
                for(Object listener:((Map<?,?>)field.get(FMLCommonHandler.instance().bus())).keySet())
                    if(listener instanceof ClientHooks) { check(hooks==null,"only one water controller");hooks=(ClientHooks)listener; }
                check(hooks!=null,"merged water controller registered");
                check(hooks.toggle.func_151463_i()==Keyboard.KEY_F9,"F9 is default binding");
                check(Arrays.asList(mc.field_71474_y.field_74324_K).contains(hooks.toggle),"binding in Controls");
                log("PASS: Forge release loaded, client-only handshake, all sound categories zero, rebindable F9, no chat command");
                round=Integer.getInteger("waterauto.test.startRound",0);
                started=true;deadline=System.currentTimeMillis()+780000;
                mc.field_71474_y.field_82881_y=false;
                mc.func_71371_a("water-isolated","Water maze isolated test",new WorldSettings(2103,WorldSettings.GameType.SURVIVAL,false,false,WorldType.field_77138_c));
                return;
            }
            if(serverError!=null) throw new AssertionError("Server fixture error",serverError);
            if(System.currentTimeMillis()>deadline) throw new AssertionError("Timeout: "+hooks.status());
            if(playing&&(mc.field_71439_g==null||mc.field_71441_e==null)) throw new AssertionError("Disconnected: "+hooks.status());
            if(!seeded||!inventoryPrepared||mc.field_71439_g==null||mc.field_71441_e==null) return;
            if(!playing) {
                net.minecraft.tileentity.TileEntity tile=mc.field_71441_e.func_147438_o(0,6,0);
                if(!(tile instanceof TileEntityWaterGameCore)||!((TileEntityWaterGameCore)tile).generated
                        ||((TileEntityWaterGameCore)tile).puzzleSeed!=core.puzzleSeed || Math.abs(mc.field_71439_g.field_70165_t+4.5)>.2) return;
                check(mc.field_71441_e.func_147439_a(-5,5,6)==Blocks.field_150348_b,"fixture floor loaded on client");
                if(mc.field_71462_r!=null) mc.func_147108_a(null);
                if(++clientTicks<50) return;
                if(!controlsChecked) { lifecycle(mc);controlsChecked=true; }
                press();release();check(hooks.active(),"F9 starts real puzzle");
                playing=true;clientTicks=0;
                log("START round="+round+" seed="+core.puzzleSeed+" path="+generated.pathLength+" waterfallMissing="+generated.missing[generated.dropX][generated.dropZ]
                        +" position="+mc.field_71439_g.field_70165_t+","+mc.field_71439_g.field_70121_D.field_72338_b+","+mc.field_71439_g.field_70161_v);
                return;
            }
            muted(mc);clientTicks++;
            if(round==2 && !resumeChecked && hooks.active()) {
                Field sf=ClientHooks.class.getDeclaredField("session");sf.setAccessible(true);Object session=sf.get(hooks);
                Field pf=session.getClass().getDeclaredField("phase"),ix=session.getClass().getDeclaredField("index");
                pf.setAccessible(true);ix.setAccessible(true);
                if(pf.get(session).toString().equals("PLACE") && ix.getInt(session)>=4) {
                    press();release();check(!hooks.active(),"mid-puzzle stop");
                    check(!mc.field_71474_y.field_74351_w.func_151470_d()&&!mc.field_71474_y.field_74314_A.func_151470_d(),"mid-puzzle stop releases movement");
                    press();release();check(hooks.active(),"resume partially completed puzzle");resumeChecked=true;
                    log("PASS: key stops mid-puzzle, releases movement, and resumes existing progress");
                }
            }
            String status=hooks.status();
            if(!status.equals(previous)) { log(status);previous=status; }
            if(clientTicks%40==0) log("POSITION "+mc.field_71439_g.field_70165_t+","+mc.field_71439_g.field_70121_D.field_72338_b+","+mc.field_71439_g.field_70161_v);
            if(!hooks.active()) {
                check(status.contains("喷泉已通水"),"completed puzzle: "+status);
                if(!rewardConfirmed && ++settle<150) return;
                check(flowConfirmed,"server confirms real water at fountain");
                check(rewardConfirmed,"original trial judge grants magic trial powder");
                check(!mc.field_71474_y.field_74351_w.func_151470_d()&&!mc.field_71474_y.field_74314_A.func_151470_d(),"movement keys released");
                log("PASS round="+round+": survival, original randomized puzzle, real movement/dig/place/inventory packets, fountain, original trial reward");
                if(++round>=3) { check(resumeChecked,"partial resume tested");finish(mc,null);return; }
                playing=false;seeded=false;clientTicks=0;settle=0;requestSeed=true;
            }
        } catch(Throwable error) { finish(mc,error); }
    }
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
        press();check(hooks.active(),"F9 starts");press();press();check(hooks.active(),"hold/repeat guard");
        release();press();check(!hooks.active(),"second press stops");release();
        mc.field_71462_r=new GuiChat();press();release();check(!hooks.active(),"typing in chat cannot activate");
        mc.field_71462_r=null;release();check(!hooks.active(),"menu key queue drained");
        hooks.toggle.func_151462_b(-97);KeyBinding.func_74508_b();
        press();check(hooks.active(),"mouse rebind starts");release();press();check(!hooks.active(),"mouse rebind stops");release();
        hooks.toggle.func_151462_b(0);KeyBinding.func_74508_b();press();release();check(!hooks.active(),"unbound disabled");
        hooks.toggle.func_151462_b(Keyboard.KEY_F9);KeyBinding.func_74508_b();
        press();release();mc.field_71439_g.field_71071_by.field_70461_c=1;
        hooks.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));check(!hooks.active(),"manual hotbar change stops");
        mc.field_71439_g.field_71071_by.field_70461_c=0;
        press();release();mc.field_71462_r=new GuiIngameMenu();hooks.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        check(!hooks.active(),"opening menu stops");mc.field_71462_r=null;
        log("PASS: F9 toggle, repeat guard, chat/menu guard, mouse rebinding, unbound, manual slot change, menu cancellation");
    }
    private void finish(Minecraft mc,Throwable error) {
        done=true;
        try {
            if(hooks!=null&&hooks.active()) hooks.stop("测试结束");
            if(error!=null) { StringWriter s=new StringWriter();error.printStackTrace(new PrintWriter(s));report.add(s.toString()); }
            report.add("checks="+checks);report.add(error==null?"status=PASS":"status=FAIL");
            Files.write(root.resolve("result.txt"),report,StandardCharsets.UTF_8);
        } catch(Exception ex) { ex.printStackTrace(); }
        mc.func_71400_g();
    }
}
