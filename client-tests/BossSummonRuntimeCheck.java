package local.bosssummontest;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.*;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.*;
import cpw.mods.fml.common.network.internal.*;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import local.foxfoxm3tools.bosssummon.BossSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.opengl.GL11;
import project.studio.manametalmod.ManaMetalMod;
import project.studio.manametalmod.bosssummon.*;

@Mod(modid="bosssummonquicktest", name="Boss Summon Shortcut Test", version="1",
        dependencies="required-after:manametalmod;required-after:foxfoxm3tools")
public final class BossSummonRuntimeCheck {
    private boolean done;
    private int checks;
    private Minecraft mc;
    private WorldClient world;
    private EntityClientPlayerMP player;
    private TileEntityBossSpawn tile;
    private Path root;
    private final List<String> report = new ArrayList<String>();
    private final List<byte[]> packets = new ArrayList<byte[]>();
    private final NBTTagList cases = new NBTTagList();

    @Mod.EventHandler public void init(FMLInitializationEvent event) { FMLCommonHandler.instance().bus().register(this); }
    private void check(boolean pass, String label) { checks++; if (!pass) throw new AssertionError(label); }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) throws Exception {
        mc = Minecraft.func_71410_x();
        if (done || event.phase != TickEvent.Phase.END || !(mc.field_71462_r instanceof GuiMainMenu)) return;
        done = true;
        root = Paths.get(System.getProperty("bosssummonquick.test.root"));
        if (!mc.field_71412_D.getCanonicalFile().equals(root.resolve("client").toFile().getCanonicalFile()))
            throw new IllegalStateException("Not the isolated client");
        try {
            local.foxfoxvalidation.MergedIdentity.verify();
            capture(); fresh();
            configurationAndLayout();
            allBosses();
            handAndLifecycle();
            NBTTagCompound data = new NBTTagCompound(); data.func_74782_a("Cases", cases);
            try (OutputStream out = Files.newOutputStream(root.resolve("packet-cases.nbt"))) { CompressedStreamTools.func_74799_a(data, out); }
            report.add("PASS: exported 13 actual original MessageDarkMain summon packets for server replay");
            report.add("checks=" + checks); report.add("status=PASS");
        } catch (Throwable failure) {
            StringWriter trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace));
            report.add(trace.toString()); report.add("status=FAIL");
        } finally {
            BossSettings.load(new File(mc.field_71412_D, "config"));
            mc.field_71462_r = null; mc.field_71439_g = null; mc.field_71441_e = null; mc.field_71442_b = null;
            mc.func_147108_a(new GuiMainMenu());
            Files.write(root.resolve("result.txt"), report, StandardCharsets.UTF_8); mc.func_71400_g();
        }
    }
    private void capture() {
        for (String name : NetworkRegistry.INSTANCE.channelNamesFor(Side.CLIENT)) {
            FMLEmbeddedChannel channel = NetworkRegistry.INSTANCE.getChannel(name, Side.CLIENT);
            if (channel.pipeline().get(FMLOutboundHandler.class) != null)
                channel.pipeline().replace(FMLOutboundHandler.class, "test-transport", new ChannelOutboundHandlerAdapter() {
                    @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
                        if (message instanceof FMLProxyPacket) {
                            ByteBuf data = ((FMLProxyPacket) message).payload().duplicate();
                            if (data.readableBytes() == 25 && data.readUnsignedByte() == 51) {
                                byte[] body = new byte[24]; data.readBytes(body); packets.add(body);
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
        world.func_73025_a(0, 0, true);
        world.func_147465_d(8, 100, 8, BossSummonCore.BlockTileEntityBossSpawns, 0, 3);
        tile = new TileEntityBossSpawn(); world.func_147455_a(8, 100, 8, tile);
        Arrays.fill(tile.spawnData, true);
        player.func_70107_b(8.5, 100, 10.5);
    }
    private void step() { FMLCommonHandler.instance().bus().post(new TickEvent.ClientTickEvent(TickEvent.Phase.END)); }
    private void reset() {
        mc.func_147108_a(null); packets.clear(); tile.isStart = false;
        Arrays.fill(tile.spawnData, true); Arrays.fill(player.field_71071_by.field_70462_a, null);
        player.field_71071_by.field_70461_c = 0;
    }
    private void held(ItemStack item) { player.field_71071_by.field_70462_a[0] = item; }
    private ItemStack crystal() { return new ItemStack(ManaMetalMod.ManaCrystal, 64); }
    private PlayerInteractEvent interact() {
        PlayerInteractEvent event = new PlayerInteractEvent(player, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, 8, 100, 8, 1, world);
        MinecraftForge.EVENT_BUS.post(event); return event;
    }
    private GuiTileEntityBossSummon open() {
        check(BossSummonCore.BlockTileEntityBossSpawns.func_149727_a(world, 8, 100, 8, player, 1, .5F, .5F, .5F), "original altar activation accepted");
        check(mc.field_71462_r instanceof GuiTileEntityBossSummon, "original block opens actual boss GUI");
        return (GuiTileEntityBossSummon) mc.field_71462_r;
    }
    @SuppressWarnings("unchecked") private List<GuiButton> buttons(GuiScreen gui) {
        return ReflectionHelper.getPrivateValue(GuiScreen.class, gui, "field_146292_n", "buttonList");
    }
    private GuiButton button(GuiScreen gui, int id) {
        for (GuiButton b : buttons(gui)) if (b.field_146127_k == id) return b;
        throw new AssertionError("Missing button " + id);
    }
    private void click(GuiTileEntityBossSummon gui, int id) {
        GuiButton b = button(gui, id);
        gui.func_73864_a(b.field_146128_h + 2, b.field_146129_i + 2, 0);
    }
    private void select(GuiTileEntityBossSummon gui, int id) {
        gui.func_73864_a(gui.guiLeft + 12 + id * 17, gui.guiTop + 10, 0); step();
        check(BossQuickSummonAccess.selected(gui) == id, "real boss icon selects " + id);
    }
    private void configurationAndLayout() throws Exception {
        Path config = Files.createTempDirectory(root, "boss-config-");
        BossSettings.load(config.toFile());
        check(BossSettings.selected() == -1, "shortcut off by default");
        reset(); held(crystal()); interact(); GuiTileEntityBossSummon gui = open(); step();
        check(mc.field_71462_r == gui && packets.isEmpty(), "crystal with no saved boss opens normally");
        check(buttons(gui).size() == 3 && !button(gui,27981).field_146124_l, "original summon plus two shortcut buttons");
        select(gui, 12); click(gui, 27981);
        check(BossSettings.selected() == 12 && mc.field_71462_r == gui && packets.isEmpty(), "save neither summons nor closes");
        BossSettings.load(config.toFile()); check(BossSettings.selected() == 12, "boss persists across config reload");
        for (int[] size : new int[][]{{640,360},{320,240},{280,240}}) {
            gui.func_146280_a(mc,size[0],size[1]); step();
            check(buttons(gui).size() == 3, "resize does not duplicate buttons");
            for (int id : new int[]{27981,27982}) {
                GuiButton b=button(gui,id);
                check(b.field_146128_h>=0 && b.field_146128_h+b.field_146120_f<=size[0]
                        && b.field_146129_i>=gui.guiTop+gui.ySize && b.field_146129_i+32<size[1], "buttons and hint fit outside original panel");
            }
            draw(gui,"boss-shortcut-"+size[0]+".png");
        }
        click(gui,27982); check(BossSettings.selected()==-1, "clear disables shortcut");
        BossSettings.load(config.toFile()); check(BossSettings.selected()==-1,"clear persisted");
        tile.spawnData[3]=false; select(gui,3);
        check(!button(gui,27981).field_146124_l,"locked boss cannot be saved");
        Files.write(config.resolve("foxfoxm3tools-bosssummon.cfg"),"quickSummon {\n S:boss=UNKNOWN\n}\n".getBytes(StandardCharsets.UTF_8));
        BossSettings.load(config.toFile()); check(BossSettings.selected()==-1,"unknown saved value safely disables shortcut");
        report.add("PASS: original boss selection/save/clear, persistent setting, default off, invalid setting, locked selection, 3 GUI sizes and screenshots");
    }
    private void allBosses() throws Exception {
        for (int id=0;id<BossType.values().length;id++) {
            reset(); held(null); GuiTileEntityBossSummon gui=open(); select(gui,id); click(gui,27981);
            check(BossSettings.selected()==id,"save actual selected boss");
            reset(); held(crystal()); interact(); open(); step();
            check(mc.field_71462_r==null && packets.size()==1,"one shortcut summon and original close");
            byte[] body=packets.get(0);
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(body));
            check(in.readInt()==4 && in.readInt()==id && in.readInt()==8 && in.readInt()==100
                    && in.readInt()==8 && in.readInt()==0,"original wire ID/boss/coordinates/count");
            for(int t=0;t<8;t++) step();
            check(packets.size()==1,"no repeated packets after close");
            check(player.func_71045_bC().field_77994_a==64 && !tile.isStart && tile.time==0,"client does not alter materials or server timer");
            NBTTagCompound test=new NBTTagCompound(); test.func_74768_a("Boss",id);test.func_74773_a("Body",body);cases.func_74742_a(test);
        }
        report.add("PASS: all 13 bosses via original altar/GUI/codec, exactly one summon packet, correct coordinates, no client-side resource or timer changes");
    }
    private void handAndLifecycle() throws Exception {
        BossSettings.save(0);
        for (ItemStack held : new ItemStack[]{null,new ItemStack(Items.field_151045_i),new ItemStack(Items.field_151055_y),new ItemStack(BossSummonCore.bossSummonItem)}) {
            reset(); held(held); player.field_71071_by.field_70462_a[9]=crystal();
            interact(); GuiTileEntityBossSummon gui=open(); step();
            check(mc.field_71462_r==gui && packets.isEmpty(),"inventory crystals do not trigger while hand empty/other item");
        }
        reset(); held(crystal()); GuiTileEntityBossSummon gui=open();step();
        check(mc.field_71462_r==gui && packets.isEmpty(),"opening GUI without right click does not trigger");
        select(gui,2); click(gui,0);
        check(packets.size()==1 && mc.field_71462_r==null,"original manual summon remains functional");
        reset();held(crystal());interact();gui=open();held(new ItemStack(Items.field_151045_i));step();
        check(packets.isEmpty() && mc.field_71462_r==gui,"switching held item before execution cancels");
        reset();held(new ItemStack(Items.field_151045_i));interact();gui=open();held(crystal());step();
        check(packets.isEmpty() && mc.field_71462_r==gui,"switching to crystal after ordinary click does not arm");
        for(boolean busy:new boolean[]{false,true}) {
            reset();held(crystal());tile.isStart=busy;tile.spawnData[0]=busy;interact();gui=open();step();
            check(packets.isEmpty() && mc.field_71462_r==gui,"locked/active altar keeps manual interface");
        }
        for(boolean canceled:new boolean[]{false,true}) {
            reset();held(crystal());
            PlayerInteractEvent e=new PlayerInteractEvent(player,PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK,8,100,8,1,world);
            if(canceled)e.setCanceled(true);else e.useBlock=Event.Result.DENY;
            MinecraftForge.EVENT_BUS.post(e);gui=open();step();
            check(packets.isEmpty() && mc.field_71462_r==gui,"canceled or denied interaction not automated");
        }
        reset();held(crystal());interact(); for(int t=0;t<40;t++)step();gui=open();step();
        check(packets.isEmpty(),"expired interaction does not arm later GUI");
        reset();held(crystal());interact();gui=open();mc.func_147108_a(new GuiInventoryForTest());step();
        check(packets.isEmpty(),"switching GUI cancels pending summon");
        reset();held(crystal());interact();gui=open();player.func_70107_b(100,100,100);step();
        check(packets.isEmpty(),"leaving altar range cancels");player.func_70107_b(8.5,100,10.5);
        reset();held(crystal());interact();gui=open();world.func_147475_p(8,100,8);step();
        check(packets.isEmpty(),"removed tile cancels"); world.func_147455_a(8,100,8,tile);tile.func_145829_t();
        reset();held(crystal());interact();gui=open();BossSettings.save(1);step();
        check(packets.isEmpty(),"changed saved choice cancels old request");
        report.add("PASS: strict held-crystal trigger; empty/other hand with inventory crystals opens normally; manual summon, locked/active altar, canceled interaction, timeout, GUI/item/choice changes, range and removed tile guards");
    }
    public static class GuiInventoryForTest extends GuiScreen { }
    private void draw(GuiTileEntityBossSummon gui,String name) {
        mc.func_147110_a().func_147610_a(true);
        GL11.glClearColor(.09F,.105F,.13F,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,gui.field_146294_l,gui.field_146295_m,0,1000,3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glTranslatef(0,0,-2000);
        gui.func_73863_a(0,0,0);
        MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.DrawScreenEvent.Post(gui,0,0,0));
        ScreenShotHelper.func_148259_a(root.toFile(),name,mc.field_71443_c,mc.field_71440_d,mc.func_147110_a());
    }
}
