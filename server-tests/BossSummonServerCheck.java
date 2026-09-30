package local.bosssummonservertest;

import com.mojang.authlib.GameProfile;
import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.*;
import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.*;
import project.studio.manametalmod.MMM;
import project.studio.manametalmod.ManaMetalMod;
import project.studio.manametalmod.blueprint.Schematic;
import project.studio.manametalmod.bosssummon.*;
import project.studio.manametalmod.network.MessageDarkMain;

@Mod(modid="bosssummonservertest", name="Boss Summon Packet Replay", version="1", dependencies="required-after:manametalmod")
public final class BossSummonServerCheck {
    private final List<String> report=new ArrayList<String>();
    private int checks;
    private WorldServer world;
    private EntityPlayerMP player;
    private TileEntityBossSpawn tile;
    private MessageContext context;
    private void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    @Mod.EventHandler public void started(FMLServerStartedEvent event) throws Exception {
        MinecraftServer server=MinecraftServer.func_71276_C();
        if(!Paths.get(".").toRealPath().equals(Paths.get(System.getProperty("bosssummonquick.server.root","invalid")).toRealPath()))
            throw new IllegalStateException("Not an isolated test server");
        try {
            check(server.func_71233_x()==0 && !Loader.isModLoaded("foxfoxm3tools"),"server has no real players or client addon");
            world=server.func_71218_a(0);
            player=new EntityPlayerMP(server,world,new GameProfile(UUID.fromString("4167508c-602b-499a-8415-000000000001"),"BossReplay"),new ItemInWorldManager(world));
            player.func_70107_b(8.5,100,10.5);
            player.field_71134_c.func_73076_a(WorldSettings.GameType.SURVIVAL);
            player.field_71135_a=new NetHandlerPlayServer(server,new NetworkManager(false),player){
                @Override public void func_147359_a(Packet packet){ }
            };
            Constructor<MessageContext> ctor=MessageContext.class.getDeclaredConstructor(INetHandler.class,Side.class);
            ctor.setAccessible(true); context=ctor.newInstance(player.field_71135_a,Side.SERVER);
            arena();
            NBTTagCompound data;
            try(InputStream in=Files.newInputStream(Paths.get("packet-cases.nbt"))){data=CompressedStreamTools.func_74796_a(in);}
            NBTTagList cases=data.func_150295_c("Cases",10);
            check(cases.func_74745_c()==13,"all thirteen recorded client choices");
            String[] types={"BossDestroyer","BossRescures","BossDragonShadow","BossLavaGiant","BossDeadAngel","BossWitheredDevil",
                    "BossDarkKnight","BossSnakeWind","BossDragonEvil","BossHydra","BossRuneGiant","MobPaganPuppetBoss","BossSkyDragon"};
            for(int i=0;i<cases.func_74745_c();i++){
                NBTTagCompound test=cases.func_150305_b(i);int id=test.func_74762_e("Boss");byte[] packet=test.func_74770_j("Body");
                check(id==i,"recorded boss order");
                reset(64);send(packet);
                int need=TileEntityBossSpawn.getNeed(BossType.values()[id]);
                check(tile.isStart && tile.type==BossType.values()[id] && tile.time==0,"server accepts recorded selected boss");
                check(crystals()==64-need,"server consumes original crystal cost for "+id);
                send(packet);check(crystals()==64-need && tile.time==0,"duplicate pending request consumes no extra materials");
                for(int tick=0;tick<120;tick++)tile.func_145845_h();
                check(tile.isStart && tile.time==120 && bosses().isEmpty(),
                        "original 120-tick ritual not bypassed: id="+id+", active="+tile.isStart+", time="+tile.time+", entities="+bosses());
                tile.func_145845_h();List<Entity> spawned=bosses();
                check(!tile.isStart && spawned.size()==1 && spawned.get(0).getClass().getSimpleName().equals(types[id]),
                        "correct boss appears after original delay: id="+id+", active="+tile.isStart+", entities="+spawned);
                reset(need-1);send(packet);check(!tile.isStart && crystals()==need-1,"insufficient crystals rejected without consumption");
                reset(64);tile.spawnData[id]=false;send(packet);check(!tile.isStart && crystals()==64,"locked boss rejected without consumption");
            }
            report.add("PASS: 13 actual client packets accepted by original MessageDarkMain handler without FoxFoxM3Tools on server");
            report.add("PASS: original 8/10/16/20 crystal costs, all 13 correct boss entities, original 120-tick ritual, duplicate/insufficient/locked requests preserve materials");
            byte[] packet=cases.func_150305_b(0).func_74770_j("Body");
            reset(64);
            // Outside the 9x9 altar schematic but inside its 12-block arena.
            world.func_147468_f(18,99,8);
            check(!TileEntityBossSpawn.hasProperArena(world,8,100,8),"fixture has a real arena-floor gap");send(packet);
            check(!tile.isStart && crystals()==64,"invalid arena floor rejected");
            world.func_147465_d(18,99,8,Blocks.field_150348_b,0,3);
            for(int x=1;x<=15;x++)for(int z=1;z<=15;z++)world.func_147465_d(x,110,z,Blocks.field_150348_b,0,3);
            check(!MMM.canSummonBoss(world,8,100,8),"fixture blocks sky access");
            send(packet);check(!tile.isStart && crystals()==64,"covered sky rejected");
            for(int x=1;x<=15;x++)for(int z=1;z<=15;z++)world.func_147468_f(x,110,z);
            Schematic schematic=TileEntityBossSpawn.tileMB.getSchematic();
            boolean removed=false;
            for(int y=0,index=0;y<schematic.height;y++)for(int z=0;z<schematic.length;z++)for(int x=0;x<schematic.width;x++,index++){
                if(!removed && !"minecraft:air".equals(schematic.blockName[index]) && (x!=4 || y!=0 || z!=4)){
                    world.func_147468_f(4+x,100+y,4+z); removed=true;
                }
            }
            check(removed && !TileEntityBossSpawn.tileMB.testBossSummon(world,4,100,4),"removed an actual required structure block");send(packet);
            check(!tile.isStart && crystals()==64,"incomplete altar structure rejected");
            report.add("PASS: original arena, open-sky and multiblock restrictions enforced; failures consume no crystals");
            report.add("checks="+checks);report.add("status=PASS");
        }catch(Throwable failure){StringWriter trace=new StringWriter();failure.printStackTrace(new PrintWriter(trace));report.add(trace.toString());report.add("status=FAIL");}
        finally{Files.write(Paths.get("result.txt"),report,StandardCharsets.UTF_8);server.func_71263_m();}
    }
    private void arena(){
        for(int x=-8;x<=24;x++)for(int z=-8;z<=24;z++){
            world.func_72964_e(x>>4,z>>4);
            world.func_147465_d(x,99,z,Blocks.field_150348_b,0,3);
            for(int y=100;y<=111;y++)world.func_147468_f(x,y,z);
        }
        Schematic s=TileEntityBossSpawn.tileMB.getSchematic();check(s!=null,"original altar schematic loaded");
        for(int y=0,index=0;y<s.height;y++)for(int z=0;z<s.length;z++)for(int x=0;x<s.width;x++,index++){
            String[] name=s.blockName[index].split(":");Block block=GameRegistry.findBlock(name[0],name[1]);
            check(block!=null,"registered schematic block");
            if(block!=Blocks.field_150350_a)world.func_147465_d(4+x,100+y,4+z,block,s.data[index],3);
        }
        world.func_147465_d(8,100,8,BossSummonCore.BlockTileEntityBossSpawns,0,3);
        check(TileEntityBossSpawn.tileMB.testBossSummon(world,4,100,4),"original multiblock valid");
        check(TileEntityBossSpawn.hasProperArena(world,8,100,8),"original arena valid");
        check(MMM.canSummonBoss(world,8,100,8),"original sky valid");
    }
    private List<Entity> bosses(){
        List<Entity> result=new ArrayList<Entity>();
        for(Object value:world.field_72996_f)if(value instanceof IBossDisplayData && !((Entity)value).field_70128_L)result.add((Entity)value);
        return result;
    }
    private void reset(int crystals){
        // Some ManaMetal bosses deliberately reject setDead while alive. Remove
        // completed test entities from this fixture explicitly between cases.
        for(Entity entity:bosses()){
            entity.field_70128_L=true;
            world.func_72900_e(entity);
            world.field_72996_f.remove(entity);
        }
        tile=new TileEntityBossSpawn();world.func_147455_a(8,100,8,tile);Arrays.fill(tile.spawnData,true);
        Arrays.fill(player.field_71071_by.field_70462_a,null);player.field_71071_by.field_70461_c=0;
        player.field_71071_by.field_70462_a[0]=new ItemStack(ManaMetalMod.ManaCrystal,crystals);
    }
    private int crystals(){ItemStack held=player.func_71045_bC();return held==null?0:held.field_77994_a;}
    private void send(byte[] body){
        ByteBuf data=Unpooled.wrappedBuffer(body);MessageDarkMain request=new MessageDarkMain();
        try{request.fromBytes(data);}finally{data.release();}
        new MessageDarkMain().onMessage(request,context);
    }
}
