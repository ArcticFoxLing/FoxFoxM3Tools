package local.skillrowservertest;

import com.mojang.authlib.GameProfile;
import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.*;
import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.*;
import project.studio.manametalmod.MMM;
import project.studio.manametalmod.core.CareerCore;
import project.studio.manametalmod.entity.nbt.ManaMetalModRoot;
import project.studio.manametalmod.items.ItemToolDaggerBase;
import project.studio.manametalmod.network.MessageSkill;
import project.studio.manametalmod.potion.*;
import project.studio.manametalmod.spell.*;

@Mod(modid="skillrowservertest", name="Skill Row Packet Replay", version="1", dependencies="required-after:manametalmod")
public final class SkillRowServerCheck {
    private int checks;
    private final List<String> report = new ArrayList<String>();
    private EntityPlayerMP player;
    private ManaMetalModRoot data;
    private Item dagger;
    private MessageContext context;
    private void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
    @Mod.EventHandler public void started(FMLServerStartedEvent event) throws Exception {
        MinecraftServer server = MinecraftServer.func_71276_C();
        if (!Paths.get(".").toRealPath().equals(Paths.get(System.getProperty("skillrow.server.root", "invalid")).toRealPath()))
            throw new IllegalStateException("Not an isolated server");
        try {
            check(server.func_71233_x() == 0 && !Loader.isModLoaded("foxfoxm3tools"), "no players and no client addon installed");
            WorldServer world = server.func_71218_a(0); world.func_72964_e(0, 0);
            player = new EntityPlayerMP(server, world, new GameProfile(UUID.fromString("4167508c-602b-499a-8415-000000000007"), "SkillReplay"), new ItemInWorldManager(world));
            player.func_70107_b(8.5, 100, 8.5); player.field_71134_c.func_73076_a(WorldSettings.GameType.SURVIVAL);
            player.field_71135_a = new NetHandlerPlayServer(server, new NetworkManager(false), player) {
                @Override public void func_147359_a(Packet packet) { }
            };
            Constructor<MessageContext> ctor = MessageContext.class.getDeclaredConstructor(INetHandler.class, Side.class);
            ctor.setAccessible(true); context = ctor.newInstance(player.field_71135_a, Side.SERVER);
            data = MMM.getEntityNBT(player); check(data != null, "real player NBT");
            for (Object value : Item.field_150901_e) if (value instanceof ItemToolDaggerBase) { dagger = (Item)value; break; }
            check(dagger != null, "original registered dagger available");
            data.carrer.setCareerType(CareerCore.Assassin.ID); data.carrer.setLV(120);
            data.mana.setMagicMax(1000000);
            NBTTagCompound input;
            try (InputStream in = Files.newInputStream(Paths.get("packet-cases.nbt"))) { input = CompressedStreamTools.func_74796_a(in); }
            NBTTagList cases = input.func_150295_c("Cases", 10);
            check(cases.func_74745_c() == 2, "two rows exported by client");
            for (int c = 0; c < cases.func_74745_c(); c++) {
                NBTTagList packets = cases.func_150305_b(c).func_150295_c("Packets", 10);
                check(packets.func_74745_c() == 7, "all seven occupied slots sent");
                reset(); int start = data.mana.getMana(); int cost = 0;
                for (int p = 0; p < packets.func_74745_c(); p++) {
                    byte[] body = packets.func_150305_b(p).func_74770_j("Body");
                    int id = id(body); Spell spell = SpellData.getSpellIDFromInt(id, CareerCore.Assassin);
                    check(spell != null && cd(id) == 0, "cast starts ready: " + id);
                    int before = data.mana.getMana(); send(body);
                    check(cd(id) > 0, "original handler starts cooldown for each consecutive spell: " + id);
                    check(data.mana.getMana() < before, "each cast consumes original mana: " + id);
                    cost += before - data.mana.getMana();
                }
                check(start - data.mana.getMana() == cost && cost > 0, "row costs accumulate");
                int remaining = data.mana.getMana(); replay(packets);
                check(data.mana.getMana() == remaining, "cooldowns prevent repeat consumption");
                reset(); data.mana.setPower(0); replay(packets); check(noCooldowns() && data.mana.getMana() == 0, "no mana rejects whole row");
                reset(); player.field_71071_by.field_70462_a[0] = null; replay(packets); check(noCooldowns(), "missing weapon rejects row");
                reset(); Arrays.fill(data.carrer.spellLV_1, 0); Arrays.fill(data.carrer.spellLV_2, 0); Arrays.fill(data.carrer.spellLV_3, 0);
                replay(packets); check(noCooldowns() && data.mana.getMana() == 1000000, "unlearned skills rejected without cost");
                reset(); PotionEffectM3.addPotion(data, PotionM3.potionSilence, 30, 0); replay(packets);
                check(noCooldowns(), "original silence restriction retained"); PotionEffectM3.removePotion(data, PotionM3.potionSilence);
                reset(); int blocked = id(packets.func_150305_b(0).func_74770_j("Body")); setCD(blocked, 500);
                replay(packets); check(cd(blocked) == 500, "cooling-down first skill skipped by server");
                for (int p = 1; p < packets.func_74745_c(); p++) check(cd(id(packets.func_150305_b(p).func_74770_j("Body"))) > 0, "later ready skills still cast");
                reset(); data.carrer.spellLV_1[5] = 1; data.mana.setPower(500000); replay(packets);
                for (int p = 0; p < packets.func_74745_c(); p++) check(cd(id(packets.func_150305_b(p).func_74770_j("Body"))) > 0, "all spells cast with Trinity passive enabled");
                check(data.mana.getMana() > 500000, "original Trinity combo mana refund remains active");
            }
            report.add("PASS: both actual client rows replayed through original MessageSkill handler without client addon on server; seven distinct Assassin skills each start cooldown and consume mana consecutively in one server tick");
            report.add("PASS: original cooldown, zero mana, missing weapon, unlearned skills and silence restrictions; first skill on cooldown does not prevent later ready skills");
            report.add("PASS: Assassin Trinity passive retains original combo mana refunds while both seven-skill rows complete");
            report.add("checks=" + checks); report.add("status=PASS");
        } catch (Throwable failure) {
            StringWriter trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace)); report.add(trace.toString()); report.add("status=FAIL");
        } finally { Files.write(Paths.get("result.txt"), report, StandardCharsets.UTF_8); server.func_71263_m(); }
    }
    private void reset() {
        Arrays.fill(data.carrer.spellCD_LV1, 0); Arrays.fill(data.carrer.spellCD_LV2, 0); Arrays.fill(data.carrer.spellCD_LV3, 0);
        Arrays.fill(data.carrer.spellLV_1, 1); Arrays.fill(data.carrer.spellLV_2, 1); Arrays.fill(data.carrer.spellLV_3, 1);
        // Disable passive refunds/reductions only for the isolated cost checks.
        data.carrer.spellLV_1[5] = 0; data.carrer.spellLV_2[0] = 0; data.carrer.spellLV_3[0] = 0;
        data.ManaEntityData.potions.clear();
        data.mana.setPower(1000000);
        player.field_71071_by.field_70461_c = 0; player.field_71071_by.field_70462_a[0] = new ItemStack(dagger);
        check(CareerCore.testPlayerWeapon(player, CareerCore.Assassin), "valid Assassin weapon");
    }
    private int[] cooldowns(int id) { return id < 200 ? data.carrer.spellCD_LV1 : id < 300 ? data.carrer.spellCD_LV2 : data.carrer.spellCD_LV3; }
    private int cd(int id) { return cooldowns(id)[id % 100]; }
    private void setCD(int id, int value) { cooldowns(id)[id % 100] = value; }
    private boolean noCooldowns() { for (int[] row : new int[][]{data.carrer.spellCD_LV1, data.carrer.spellCD_LV2, data.carrer.spellCD_LV3}) for (int value : row) if (value != 0) return false; return true; }
    private int id(byte[] body) throws IOException { return new DataInputStream(new ByteArrayInputStream(body)).readInt(); }
    private void replay(NBTTagList packets) { for (int p = 0; p < packets.func_74745_c(); p++) send(packets.func_150305_b(p).func_74770_j("Body")); }
    private void send(byte[] body) {
        ByteBuf bytes = Unpooled.wrappedBuffer(body); MessageSkill request = new MessageSkill();
        try { request.fromBytes(bytes); } finally { bytes.release(); }
        new MessageSkill().onMessage(request, context);
    }
}
