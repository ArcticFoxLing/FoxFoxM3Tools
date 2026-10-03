package local.outputcollecttest;
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

import local.foxfoxm3tools.outputcollect.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.*;
import project.studio.manametalmod.produce.brewing.TileEntityAdvancedBrewing;
import project.studio.manametalmod.produce.ProduceCore;
import project.studio.manametalmod.originmagic.TileEntityEternalAnvil;

@Mod(modid="outputcollecttest",name="Output Collect Runtime Test",version="1",dependencies="required-after:manametalmod;required-after:foxfoxm3tools;after:NotEnoughItems")
public final class OutputCollectRuntimeCheck {
    private boolean done;
    private int checks;
    private final List<String> report=new ArrayList<String>();
    private final NBTTagList cases=new NBTTagList();
    private Minecraft mc;
    private EntityClientPlayerMP player;
    private RecordingHandler network;
    private OutputFixtures fixture;
    private GuiContainer gui;
    private Path root;
    private int fixtureIndex;
    @Mod.EventHandler public void init(FMLInitializationEvent event){FMLCommonHandler.instance().bus().register(this);}
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message+" @ "+(fixture==null?"startup":fixture.entry.container));}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event)throws Exception {
        mc=Minecraft.func_71410_x();
        if(done||event.phase!=TickEvent.Phase.END||!(mc.field_71462_r instanceof GuiMainMenu))return;
        done=true;root=Paths.get(System.getProperty("outputcollect.test.root"));
        if(!mc.field_71412_D.getCanonicalFile().equals(root.resolve("client").toFile().getCanonicalFile()))throw new IllegalStateException("Not isolated");
        try {
            local.foxfoxvalidation.MergedIdentity.verify();
            network=new RecordingHandler(mc);
            WorldClient world=new WorldClient(network,new WorldSettings(0,WorldSettings.GameType.SURVIVAL,false,false,WorldType.field_77138_c),0,EnumDifficulty.PEACEFUL,mc.field_71424_I);
            player=new EntityClientPlayerMP(mc,world,mc.func_110432_I(),network,new StatFileWriter());
            player.func_70107_b(2.5,100,1.5);
            mc.field_71441_e=world;mc.field_71439_g=player;mc.field_71442_b=new PlayerControllerMP(mc,network);
            world.func_73025_a(0,0,true);
            world.func_147465_d(1,100,1,net.minecraft.init.Blocks.field_150486_ae,0,3);
            for(int i=0;i<OutputFixtures.ENTRIES.length;i++) {
                setup(i,0,640,360);fixture.seed();
                NBTTagCompound before=snapshot();update(8);
                check(network.clicks.isEmpty()&&before.equals(snapshot()),"default OFF makes no clicks");
                check(button().field_146125_m,"button visible at normal scale");
                check(button().field_146126_j.equals("自动收入背包：关"),"localized label");
                int[] outputs=OutputRegistry.slots(fixture.container,player);
                check(outputs!=null&&outputs.length==fixture.entry.outputs.length,"all audited output slots resolved");
                checkLayout();
                toggle();update(fixture.entry.outputs.length*6+15);
                for(int slot:fixture.entry.outputs)check(fixture.inventory.func_70301_a(slot)==null,"finished output emptied "+slot);
                check(player.field_71071_by.func_70445_o()==null,"cursor empty after collection");
                preservedInputs(before);
                saveCase("collect-all",before);
                int sent=network.clicks.size();update(20);check(sent==network.clicks.size(),"empty machine sends no packets");
                gui.func_146280_a(mc,640,360);check(collectButtons()==1,"resize has exactly one button");
                check(button().field_146126_j.endsWith("开"),"resize preserves enabled state");
                if(fixture.entry.container.endsWith("ContainerManaGravityWell")||fixture.entry.container.endsWith("ContainerTileEntityDarkMain")||fixture.entry.container.endsWith("ContainerTileEntityBlueSky")) {
                    fixture.inventory.func_70299_a(fixture.entry.outputs[0],fixture.product(fixture.entry.outputs[0]));
                    draw(fixture.entry.container.substring(fixture.entry.container.lastIndexOf('.')+1)+".png");
                    network.clicks.clear();before=snapshot();update(12);
                    check(fixture.inventory.func_70301_a(fixture.entry.outputs[0])==null,"next craft also collected");saveCase("next-production",before);
                }
                setup(i,0,640,360);fixture.seed();fillInventory();before=snapshot();toggle();update(25);
                check(network.clicks.isEmpty()&&before.equals(snapshot()),"full inventory leaves all items untouched");
                check(button().field_146126_j.contains("空间不足"),"full inventory status");saveCase("full",before);
            }
            report.add("PASS: all "+OutputFixtures.ENTRIES.length+" original containers/GUIs; default off, output-only transfers, input preservation, full inventory, unique buttons and reinitialization");
            int gravity=find("ContainerManaGravityWell");
            setup(gravity,0,640,360);fillInventory();
            ItemStack product=OutputFixtures.tagged(new ItemStack(Items.field_151045_i,16),"same");fixture.inventory.func_70299_a(12,product.func_77946_l());
            player.field_71071_by.field_70462_a[9]=product.func_77946_l();player.field_71071_by.field_70462_a[9].field_77994_a=60;
            player.field_71071_by.field_70462_a[10]=product.func_77946_l();player.field_71071_by.field_70462_a[10].field_77994_a=60;
            NBTTagCompound before=snapshot();toggle();update(15);
            check(network.clicks.isEmpty()&&before.equals(snapshot()),"insufficient partial space never picks output up");saveCase("partial-space-wait",before);
            player.field_71071_by.field_70462_a[11]=product.func_77946_l();player.field_71071_by.field_70462_a[11].field_77994_a=56;
            before=snapshot();update(10);check(fixture.inventory.func_70301_a(12)==null,"resumes when matching stack space exists");
            check(player.field_71071_by.field_70462_a[9].field_77994_a==64&&player.field_71071_by.field_70462_a[10].field_77994_a==64&&player.field_71071_by.field_70462_a[11].field_77994_a==64,"spread across three matching stacks");saveCase("partial-space-resume",before);
            setup(gravity,0,640,360);fillInventory();fixture.inventory.func_70299_a(12,product.func_77946_l());
            player.field_71071_by.field_70462_a[9]=OutputFixtures.tagged(new ItemStack(Items.field_151045_i,1),"different NBT");
            before=snapshot();toggle();update(15);check(before.equals(snapshot())&&network.clicks.isEmpty(),"different NBT never merged/swapped");saveCase("different-nbt",before);
            player.field_71071_by.field_70462_a[0]=null;before=snapshot();update(10);check(fixture.inventory.func_70301_a(12)==null,"hotbar-only free slot supported");saveCase("hotbar-space",before);
            setup(gravity,0,640,360);fixture.seed();player.field_71071_by.func_70437_b(new ItemStack(Items.field_151055_y,3));
            before=snapshot();toggle();update(15);check(before.equals(snapshot())&&network.clicks.isEmpty(),"user cursor pauses automation");
            player.field_71071_by.func_70437_b(null);before=snapshot();update(10);check(fixture.inventory.func_70301_a(12)==null,"cursor cleared resumes");saveCase("cursor-resume",before);
            fixture.inventory.func_70299_a(12,product.func_77946_l());network.clicks.clear();toggle();before=snapshot();update(15);check(before.equals(snapshot())&&network.clicks.isEmpty(),"OFF stops new products");
            toggle();mc.func_147108_a(null);network.clicks.clear();update(15);check(network.clicks.isEmpty(),"closing GUI cancels automation");
            setup(gravity,0,640,360);fixture.seed();before=snapshot();toggle();fixture.container.field_75152_c=0;update(12);check(network.clicks.isEmpty(),"pending window waits");
            fixture.container.field_75152_c=7;player.field_71070_bA=player.field_71069_bz;update(12);check(network.clicks.isEmpty(),"changed container cancels");
            for(String name:new String[]{"ContainerSpinningWheel","ContainerTileEntityBase"}) {
                setup(find(name),1,640,360);fixture.seed();before=snapshot();toggle();update(15);
                check(fixture.inventory.func_70301_a(fixture.entry.outputs[0])==null,"variant output collected");preservedInputs(before);saveCase("variant",before);
            }
            setup(find("ContainerAdvancedBrewing"),0,640,360);
            TileEntityAdvancedBrewing brew=(TileEntityAdvancedBrewing)fixture.tile;
            for(int s:new int[]{5,6,7})fixture.inventory.func_70299_a(s,new ItemStack(ProduceCore.ManaPotionBottles,1,1));
            before=snapshot();toggle();update(15);check(before.equals(snapshot())&&network.clicks.isEmpty(),"empty bottles retained");saveCase("bottles",before);
            fixture.seed();brew.Star=1;before=snapshot();update(15);check(before.equals(snapshot())&&network.clicks.isEmpty(),"in-progress brewing retained");
            setup(find("ContainerEternalAnvil"),0,640,360);fixture.seed();
            ((TileEntityEternalAnvil)fixture.tile).jobType=2;before=snapshot();toggle();update(15);check(before.equals(snapshot())&&network.clicks.isEmpty(),"reforge workpiece protected");
            ((TileEntityEternalAnvil)fixture.tile).jobType=0;fixture.inventory.func_70299_a(27,new ItemStack(Items.field_151055_y,1));before=snapshot();update(15);check(before.equals(snapshot())&&network.clicks.isEmpty(),"unrelated reforge input protected");
            for(String name:new String[]{"ContainerManaGravityWell","ContainerTileEntityDarkMain","ContainerTileEntityBlueSky","ContainerClothesTailor","ContainerCookingTable"}) {
                setup(find(name),0,320,240);check(button().field_146125_m,"compact button visible");checkLayout();
            }
            setup(gravity,0,640,360);mc.func_147108_a(new GuiScreen());update(5);
            check(!OutputRegistry.supports(player.field_71069_bz),"player inventory not a crafting target");
            report.add("PASS: consecutive products; NBT, all-or-nothing capacity, three-stack merge, hotbar-only space, cursor/manual pause, close/cancel, variant slot ordering, empty bottles and working reforge protections, compact layouts");
            NBTTagCompound data=new NBTTagCompound();data.func_74782_a("Cases",cases);
            try(OutputStream out=Files.newOutputStream(root.resolve("packet-cases.nbt"))){CompressedStreamTools.func_74799_a(data,out);}
            report.add("PASS: exported "+cases.func_74745_c()+" actual client packet sequences for authoritative server replay");
            report.add("PASS: "+checks+" assertions; startup and every sound category verified muted");report.add("status=PASS");
        }catch(Throwable failure){StringWriter trace=new StringWriter();failure.printStackTrace(new PrintWriter(trace));report.add("status=FAIL\n"+trace);}
        finally {mc.field_71439_g=null;mc.field_71441_e=null;mc.field_71462_r=null;mc.field_71442_b=null;mc.func_147108_a(new GuiMainMenu());Files.write(root.resolve("result.txt"),report,StandardCharsets.UTF_8);mc.func_71400_g();}
    }
    private int find(String name){for(int i=0;i<OutputFixtures.ENTRIES.length;i++)if(OutputFixtures.ENTRIES[i].container.endsWith("."+name))return i;throw new AssertionError(name);}
    private void setup(int index,int variant,int width,int height)throws Exception {
        fixtureIndex=index;Arrays.fill(player.field_71071_by.field_70462_a,null);player.field_71071_by.func_70437_b(null);
        fixture=new OutputFixtures(OutputFixtures.ENTRIES[index],mc.field_71441_e,player,variant);
        java.lang.reflect.Constructor<?> ctor=Class.forName(fixture.entry.gui).getConstructors()[0];
        Class<?>[] types=ctor.getParameterTypes();Object[] args=new Object[types.length];int coordinate=0;
        for(int i=0;i<types.length;i++) {
            if(types[i].isInstance(fixture.container))args[i]=fixture.container;
            else if(types[i].isInstance(fixture.tile))args[i]=fixture.tile;
            else if(types[i]==net.minecraft.entity.player.InventoryPlayer.class)args[i]=player.field_71071_by;
            else if(types[i]==net.minecraft.world.World.class)args[i]=mc.field_71441_e;
            else if(types[i]==int.class)args[i]=new int[]{1,100,1}[coordinate++];
            else throw new AssertionError("Unhandled GUI constructor "+ctor);
        }
        gui=(GuiContainer)ctor.newInstance(args);gui.field_147002_h=fixture.container;
        mc.func_147108_a(gui);gui.func_146280_a(mc,width,height);network.clicks.clear();update(1);
    }
    private void fillInventory(){for(int i=0;i<36;i++)player.field_71071_by.field_70462_a[i]=new ItemStack(Items.field_151055_y,64);}
    @SuppressWarnings("unchecked") private List<GuiButton> buttons(){return ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"field_146292_n","buttonList");}
    private int collectButtons(){int n=0;for(GuiButton b:buttons())if(b.getClass().getName().contains("outputcollect"))n++;return n;}
    private GuiButton button(){for(GuiButton b:buttons())if(b.getClass().getName().contains("outputcollect"))return b;throw new AssertionError("collect button missing");}
    private void toggle(){MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.ActionPerformedEvent.Pre(gui,button(),buttons()));}
    private void update(int ticks){for(int i=0;i<ticks;i++)FMLCommonHandler.instance().bus().post(new TickEvent.ClientTickEvent(TickEvent.Phase.END));}
    private NBTTagCompound snapshot(){return OutputFixtures.snapshot(fixture.container,player);}
    private void preservedInputs(NBTTagCompound before) {
        NBTTagList original=before.func_150295_c("Slots",10);
        for(int i=0;i<fixture.container.field_75151_b.size();i++) {
            Slot slot=fixture.container.func_75139_a(i);
            if(slot.field_75224_c==fixture.inventory&&!fixture.output(slot.getSlotIndex()))check(original.func_150305_b(i).equals(OutputFixtures.item(slot.func_75211_c())),"input unchanged "+slot.getSlotIndex());
        }
    }
    private void checkLayout(){
        GuiButton b=button();int w=ReflectionHelper.<Integer,GuiContainer>getPrivateValue(GuiContainer.class,gui,"field_146999_f","xSize"),h=ReflectionHelper.<Integer,GuiContainer>getPrivateValue(GuiContainer.class,gui,"field_147000_g","ySize");
        int l=(gui.field_146294_l-w)/2,t=(gui.field_146295_m-h)/2;
        check(b.field_146128_h>=0&&b.field_146129_i>=0&&b.field_146128_h+b.field_146120_f<=gui.field_146294_l&&b.field_146129_i+20<=gui.field_146295_m,"on-screen button");
        check(b.field_146128_h>=l+w||b.field_146128_h+b.field_146120_f<=l||b.field_146129_i>=t+h||b.field_146129_i+20<=t,"button outside inventory");
        for(GuiButton other:buttons())if(other!=b&&other.field_146125_m)check(b.field_146128_h>=other.field_146128_h+other.field_146120_f||b.field_146128_h+b.field_146120_f<=other.field_146128_h||b.field_146129_i>=other.field_146129_i+other.field_146121_g||b.field_146129_i+20<=other.field_146129_i,"button avoids existing controls");
    }
    private void saveCase(String name,NBTTagCompound before){
        NBTTagCompound test=new NBTTagCompound();test.func_74778_a("Name",name);test.func_74768_a("Fixture",fixtureIndex);test.func_74768_a("Variant",fixture.variant);
        test.func_74782_a("Before",before);test.func_74782_a("After",snapshot());NBTTagList packets=new NBTTagList();
        for(C0EPacketClickWindow p:network.clicks){
            check(p.func_149542_h()==0&&p.func_149543_e()==0&&p.func_149544_d()>=0,"ordinary pickup/place only; never drop");
            NBTTagCompound e=new NBTTagCompound();e.func_74768_a("Window",p.func_149548_c());e.func_74768_a("Slot",p.func_149544_d());e.func_74768_a("Button",p.func_149543_e());e.func_74768_a("Mode",p.func_149542_h());e.func_74777_a("Action",p.func_149547_f());e.func_74782_a("Clicked",OutputFixtures.item(p.func_149546_g()));packets.func_74742_a(e);
        }
        test.func_74782_a("Packets",packets);cases.func_74742_a(test);network.clicks.clear();
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
