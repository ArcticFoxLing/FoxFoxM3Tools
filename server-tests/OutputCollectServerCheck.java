package local.outputcollectservertest;
import com.mojang.authlib.GameProfile;
import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import io.netty.buffer.Unpooled;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.*;
import project.studio.manametalmod.produce.beekeeping.*;

import local.outputcollecttest.OutputFixtures;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

@Mod(modid="outputcollectservertest",name="Output Collect Server Replay",version="1",dependencies="required-after:manametalmod")
public final class OutputCollectServerCheck {
    private int checks,accepted,rejected;
    private final List<String> report=new ArrayList<String>();
    private EntityPlayerMP player;
    private WorldServer world;
    private OutputFixtures fixture;
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    @Mod.EventHandler public void started(FMLServerStartedEvent event) {
        MinecraftServer server=MinecraftServer.func_71276_C();
        try {
            if(!Paths.get(".").toRealPath().equals(Paths.get(System.getProperty("outputcollect.server.root","invalid")).toRealPath()))throw new IllegalStateException("Not isolated");
            check(server.func_71233_x()==0,"no real players");check(!Loader.isModLoaded("foxfoxm3tools"),"addon absent on server");
            world=server.func_71218_a(0);world.func_72964_e(0,0);
            world.func_147465_d(1,100,1,net.minecraft.init.Blocks.field_150486_ae,0,3);
            player=new EntityPlayerMP(server,world,new GameProfile(UUID.fromString("cdbaee11-8785-4c44-9695-73383d090c44"),"OutputReplay"),new ItemInWorldManager(world));
            player.func_70107_b(2.5,100,1.5);player.field_71134_c.func_73076_a(WorldSettings.GameType.SURVIVAL);
            player.field_71135_a = new NetHandlerPlayServer(server, new NetworkManager(false), player) {
                @Override public void func_147359_a(Packet packet) {
                    if (packet instanceof S32PacketConfirmTransaction) {
                        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
                        try {
                            packet.func_148840_b(buffer);
                            buffer.readUnsignedByte(); buffer.readShort();
                            if (buffer.readBoolean()) accepted++; else rejected++;
                        } catch (IOException error) { throw new IllegalStateException(error); }
                        finally { buffer.release(); }
                    }
                }
            };

            NBTTagCompound data;try(InputStream in=Files.newInputStream(Paths.get("packet-cases.nbt"))){data=CompressedStreamTools.func_74796_a(in);}
            NBTTagList cases=data.func_150295_c("Cases",10);int packetCount=0;Set<Integer> covered=new HashSet<Integer>();
            for(int index=0;index<cases.func_74745_c();index++) {
                NBTTagCompound test=cases.func_150305_b(index);int id=test.func_74762_e("Fixture");covered.add(id);
                Arrays.fill(player.field_71071_by.field_70462_a,null);player.field_71071_by.func_70437_b(null);
                fixture=new OutputFixtures(OutputFixtures.ENTRIES[id],world,player,test.func_74762_e("Variant"));
                OutputFixtures.restore(fixture.container,player,test.func_74775_l("Before"));
                Map<String,Integer> total=counts();
                NBTTagList clicks=test.func_150295_c("Packets",10);
                for(int i=0;i<clicks.func_74745_c();i++) {
                    NBTTagCompound p=clicks.func_150305_b(i);check(p.func_74762_e("Mode")==0&&p.func_74762_e("Slot")>=0,"no drop/creative/shift commands");
                    C0EPacketClickWindow packet=new C0EPacketClickWindow();PacketBuffer buffer=new PacketBuffer(Unpooled.buffer());
                    try {
                        buffer.writeByte(p.func_74762_e("Window"));buffer.writeShort(p.func_74762_e("Slot"));buffer.writeByte(p.func_74762_e("Button"));buffer.writeShort(p.func_74765_d("Action"));buffer.writeByte(p.func_74762_e("Mode"));buffer.func_150788_a(OutputFixtures.item(p.func_74775_l("Clicked")));packet.func_148837_a(buffer);
                    }finally{buffer.release();}
                    int was=accepted;player.field_71135_a.func_147351_a(packet);
                    check(rejected==0&&accepted==was+1,"accepted client transaction "+index+" / "+fixture.entry.container+" / "+test.func_74779_i("Name")+" #"+i);packetCount++;
                }
                check(test.func_74775_l("After").equals(OutputFixtures.snapshot(fixture.container,player)),"server matches client case "+index+" "+fixture.entry.container);
                check(total.equals(counts()),"item counts, damage and NBT conserved case "+index);
                check(player.field_71071_by.func_70445_o()==null,"cursor empty case "+index);
            }
            for(Object value:world.field_72996_f)check(!(value instanceof EntityItem),"no item entities dropped");
            check(covered.size()==OutputFixtures.ENTRIES.length,"every supported container replayed");
            report.add("PASS: addon absent on server; original ManaMetal 8.0.7 containers and real NetHandlerPlayServer");
            report.add("PASS: "+covered.size()+" container types, "+cases.func_74745_c()+" cases, "+packetCount+" accepted real client packets; zero rejected transactions");
            report.add("PASS: authoritative inventory equals client predictions; all item counts and NBT conserved; no dropped items or stranded cursor");
            report.add("PASS: "+checks+" assertions");report.add("status=PASS");
        }catch(Throwable failure){StringWriter trace=new StringWriter();failure.printStackTrace(new PrintWriter(trace));report.add("status=FAIL\n"+trace);}
        finally{try{Files.write(Paths.get("result.txt"),report,StandardCharsets.UTF_8);}catch(IOException error){error.printStackTrace();}server.func_71263_m();}
    }
    private Map<String,Integer> counts(){
        Map<String,Integer> result=new TreeMap<String,Integer>();
        for(Object value:fixture.container.field_75151_b)add(result,((Slot)value).func_75211_c());
        add(result,player.field_71071_by.func_70445_o());return result;
    }
    private void add(Map<String,Integer> result,ItemStack item){
        if(item==null)return;NBTTagCompound tag=OutputFixtures.item(item);tag.func_82580_o("Count");String key=tag.toString();
        Integer n=result.get(key);result.put(key,(n==null?0:n)+item.field_77994_a);
    }
}
