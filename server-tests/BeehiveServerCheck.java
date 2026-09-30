package local.beehiveservertest;

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

@Mod(modid="beehiveservertest", name="Beehive Packet Replay Test", version="1",
        dependencies="required-after:manametalmod")
public final class BeehiveServerCheck {
    private int checks, accepted, rejected;
    private final List<String> report = new ArrayList<String>();
    private WorldServer world;
    private EntityPlayerMP player;
    private ContainerHoneycomb container;
    private TileEntityBeehive hive;
    private void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }

    @Mod.EventHandler public void started(FMLServerStartedEvent event) {
        MinecraftServer server = MinecraftServer.func_71276_C();
        try {
            if (!Paths.get(".").toRealPath().equals(Paths.get(System.getProperty("beehiveclient.server.root", "invalid")).toRealPath()))
                throw new IllegalStateException("Not the isolated server");
        } catch (IOException failure) { throw new IllegalStateException(failure); }
        try {
            check(server.func_71233_x() == 0, "no real players");
            check(!Loader.isModLoaded("foxfoxm3tools") && !Loader.isModLoaded("manametalbeehiveclient")
                    && !Loader.isModLoaded("manametalbeehiveauto"),
                    "neither client addon nor old server addon installed");
            try { BlockHoneycomb.class.getField("manaBeehiveAutoInstalled"); throw new AssertionError("old hook present"); }
            catch (NoSuchFieldException expected) {}
            world = server.func_71218_a(0); world.func_72964_e(0, 0);
            player = new EntityPlayerMP(server, world,
                    new GameProfile(UUID.fromString("cdbda2a1-8785-4c44-9695-73383d090c44"), "BeehiveReplay"), new ItemInWorldManager(world));
            player.func_70107_b(2.5, 100, 1.5);
            player.field_71134_c.func_73076_a(WorldSettings.GameType.SURVIVAL);
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
            NBTTagCompound data;
            try (InputStream in = Files.newInputStream(Paths.get("packet-cases.nbt"))) { data = CompressedStreamTools.func_74796_a(in); }
            NBTTagList cases = data.func_150295_c("Cases", 10);
            int packets = 0, tests = 0;
            for (Block tier : new Block[]{BeekeepingCore.BlockHoneycomb1, BeekeepingCore.BlockHoneycomb2, BeekeepingCore.BlockHoneycomb3}) {
                for (int index = 0; index < cases.func_74745_c(); index++) {
                    setup(tier);
                    NBTTagCompound test = cases.func_150305_b(index);
                    NBTTagCompound before = test.func_74775_l("Before");
                    NBTTagList slots = before.func_150295_c("Slots", 10);
                    for (int slot = 0; slot < 63; slot++) container.func_75139_a(slot).func_75215_d(item(slots.func_150305_b(slot)));
                    player.field_71071_by.func_70437_b(item(before.func_74775_l("Cursor")));
                    Map<String,Integer> expectedDrops = new TreeMap<String,Integer>();
                    if (test.func_74779_i("Name").startsWith("drop"))
                        for (int slot = 3; slot < 27; slot++) add(expectedDrops, hive.items[slot]);
                    NBTTagList clicks = test.func_150295_c("Packets", 10);
                    for (int i = 0; i < clicks.func_74745_c(); i++) {
                        NBTTagCompound p = clicks.func_150305_b(i);
                        // The multi-argument packet constructor is client-only in Forge.
                        // Decode the real wire format, as the remote server normally does.
                        C0EPacketClickWindow packet = new C0EPacketClickWindow();
                        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
                        try {
                            buffer.writeByte(p.func_74762_e("Window"));
                            buffer.writeShort(p.func_74762_e("Slot"));
                            buffer.writeByte(p.func_74762_e("Button"));
                            buffer.writeShort(p.func_74765_d("Action"));
                            buffer.writeByte(p.func_74762_e("Mode"));
                            buffer.func_150788_a(item(p.func_74775_l("Clicked")));
                            packet.func_148837_a(buffer);
                        } finally { buffer.release(); }
                        int priorAccepted = accepted;
                        player.field_71135_a.func_147351_a(packet);
                        check(rejected == 0 && accepted == priorAccepted + 1,
                                "server accepts actual client transaction " + test.func_74779_i("Name") + " #" + i);
                        packets++;
                    }
                    check(test.func_74775_l("After").equals(snapshot()), "authoritative server matches predicted client inventory: " + index);
                    Map<String,Integer> drops = new TreeMap<String,Integer>();
                    for (Object object : world.field_72996_f) if (object instanceof EntityItem && !((Entity)object).field_70128_L)
                        add(drops, ((EntityItem)object).func_92059_d());
                    check(expectedDrops.equals(drops), "all world drops exactly match products including NBT: " + index);
                    tests++;
                }
            }
            report.add("PASS: unmodified ManaMetal server; no beehive addon/coremod installed");
            report.add("PASS: " + tests + " scenarios across 3 hive tiers; " + packets + " recorded client C0E packets accepted by real NetHandlerPlayServer");
            report.add("PASS: authoritative inventories equal client predictions, world drops preserve counts/NBT, queen/workers unchanged by drop");
            report.add("PASS: " + checks + " assertions; ManaMetal " + Loader.instance().getIndexedModList().get("manametalmod").getVersion());
            report.add("status=PASS");
        } catch (Throwable failure) {
            StringWriter trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace)); report.add("status=FAIL\n" + trace);
        } finally {
            try { Files.write(Paths.get("result.txt"), report, StandardCharsets.UTF_8); }
            catch (IOException e) { e.printStackTrace(); }
            server.func_71263_m();
        }
    }

    private void setup(Block tier) {
        for (Object object : new ArrayList<Object>(world.field_72996_f)) if (object instanceof EntityItem) {
            world.func_72900_e((Entity)object); world.field_72996_f.remove(object);
        }
        if (hive != null) Arrays.fill(hive.items, null);
        world.func_147465_d(1, 100, 1, tier, 0, 3);
        hive = new TileEntityBeehive(); world.func_147455_a(1, 100, 1, hive);
        Arrays.fill(player.field_71071_by.field_70462_a, null); player.field_71071_by.func_70437_b(null);
        container = new ContainerHoneycomb(player.field_71071_by, hive); container.field_75152_c = 7;
        player.field_71070_bA = container;
    }
    private static ItemStack item(NBTTagCompound tag) {
        if (!tag.func_74764_b("Item")) return null;
        Item type = (Item)Item.field_150901_e.func_82594_a(tag.func_74779_i("Item"));
        if (type == null) throw new AssertionError("Missing registered item " + tag.func_74779_i("Item"));
        ItemStack stack = new ItemStack(type, tag.func_74762_e("Count"), tag.func_74762_e("Damage"));
        if (tag.func_74764_b("Tag")) stack.func_77982_d((NBTTagCompound)tag.func_74775_l("Tag").func_74737_b());
        return stack;
    }
    private static NBTTagCompound item(ItemStack stack) {
        NBTTagCompound tag = new NBTTagCompound();
        if (stack != null) {
            tag.func_74778_a("Item", Item.field_150901_e.func_148750_c(stack.func_77973_b()));
            tag.func_74768_a("Count", stack.field_77994_a); tag.func_74768_a("Damage", stack.func_77960_j());
            if (stack.func_77942_o()) tag.func_74782_a("Tag", stack.func_77978_p().func_74737_b());
        }
        return tag;
    }
    private NBTTagCompound snapshot() {
        NBTTagCompound tag = new NBTTagCompound(); NBTTagList slots = new NBTTagList();
        for (int slot = 0; slot < 63; slot++) slots.func_74742_a(item(container.func_75139_a(slot).func_75211_c()));
        tag.func_74782_a("Slots", slots); tag.func_74782_a("Cursor", item(player.field_71071_by.func_70445_o())); return tag;
    }
    private static void add(Map<String,Integer> counts, ItemStack stack) {
        if (stack == null) return;
        NBTTagCompound tag = item(stack); tag.func_82580_o("Count");
        String key = tag.toString(); Integer previous = counts.get(key);
        counts.put(key, (previous == null ? 0 : previous) + stack.field_77994_a);
    }
}
