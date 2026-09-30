package project.studio.manametalmod.produce.cuisine;

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
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.*;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.*;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.opengl.GL11;
import project.studio.manametalmod.client.GuiCookingTable;
import project.studio.manametalmod.produce.brewing.*;
import project.studio.manametalmod.produce.casting.*;
import project.studio.manametalmod.produce.textile.*;

/** Real Forge UI and codecs; original tile rules with delayed simulated transport. */
@Mod(modid="minigameautotest", name="Isolated Minigame Auto Test", version="1",
        dependencies="required-after:manametalmod;required-after:foxfoxm3tools")
public final class MinigameRuntimeCheck {
    private boolean done;
    private int checks;
    private final List<String> report = new ArrayList<String>();
    private Minecraft mc;
    private Path fixture;
    private RecordingHandler network;
    private final Field buttonList = ReflectionHelper.findField(GuiScreen.class, "field_146292_n", "buttonList");
    private final Method mouse = ReflectionHelper.findMethod(GuiScreen.class, null,
            new String[]{"func_73864_a", "mouseClicked"}, int.class, int.class, int.class);
    private TileEntityClothesTailor tailor, tailorServer;
    private TileEntityPotionMake potion, potionServer;
    private TileEntityCastingOther anvil;
    private int processed, completions, submissions, starts;
    private boolean completionValid;

    @Mod.EventHandler public void init(FMLInitializationEvent event) { FMLCommonHandler.instance().bus().register(this); }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) throws Exception {
        mc = Minecraft.func_71410_x();
        if (done || event.phase != TickEvent.Phase.END || !(mc.field_71462_r instanceof GuiMainMenu)) return;
        done = true;
        fixture = Paths.get(System.getProperty("minigameauto.test.root"));
        if (!mc.field_71412_D.getCanonicalFile().equals(fixture.resolve("client").toFile().getCanonicalFile()))
            throw new IllegalStateException("Wrong test directory");
        try {
            local.foxfoxvalidation.MergedIdentity.verify();
            ModContainer mod = Loader.instance().getIndexedModList().get("foxfoxm3tools");
            NetworkModHolder holder = NetworkRegistry.INSTANCE.registry().get(mod);
            check(holder.check(Collections.<String,String>emptyMap(), Side.SERVER), "client-only handshake");
            for (String name : NetworkRegistry.INSTANCE.channelNamesFor(Side.CLIENT)) {
                FMLEmbeddedChannel channel = NetworkRegistry.INSTANCE.getChannel(name, Side.CLIENT);
                if (channel.pipeline().get(FMLOutboundHandler.class) != null)
                    channel.pipeline().replace(FMLOutboundHandler.class, "test-transport", new ChannelOutboundHandlerAdapter() {
                        @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
                            if (network != null && message instanceof Packet) network.func_147297_a((Packet)message);
                            promise.setSuccess();
                        }
                    });
            }
            testTailorRounds(); testTailor(); testPotion(); testCooking(); testAnvil();
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

    private void testTailorRounds() throws Exception {
        fresh();
        tailor = new TileEntityClothesTailor(); attach(tailor);
        tailorServer = new TileEntityClothesTailor(); attach(tailorServer);
        WorldClient serverWorld = new WorldClient(network, new WorldSettings(0, WorldSettings.GameType.SURVIVAL,
                false, false, WorldType.field_77138_c), 0, EnumDifficulty.PEACEFUL, mc.field_71424_I) {
            @Override public boolean func_72838_d(net.minecraft.entity.Entity entity) { return true; }
        };
        ReflectionHelper.findField(World.class, "field_72995_K", "isRemote").setBoolean(serverWorld, false);
        serverWorld.field_73012_v.setSeed(91501L);
        tailorServer.func_145834_a(serverWorld);
        show(new GuiClothesTailor(mc.field_71439_g.field_71071_by, tailor));
        click(auto());
        ContainerClothesTailor container = (ContainerClothesTailor)((GuiContainer)mc.field_71462_r).field_147002_h;
        int[] materials = {2, 1, 5};
        for (int round = 0; round < 6; round++) {
            tailorServer.func_70299_a(5, null);
            tailor.func_70299_a(5, null);
            for (int slot = 0; slot < 5; slot++) {
                ItemStack stack = new ItemStack(TextileCore.ItemTextiles, 1, slot < 3 ? materials[round % 3] : 3);
                tailorServer.func_70299_a(slot, stack);
                tailor.func_70299_a(slot, stack.func_77946_l());
            }
            check(tailorServer.canStart(), "next real textile recipe can start");
            click(original()); apply(); // Actual GUI start packet; the server sends new-round NBT.
            int sent = network.requests.size();
            step(1, false);
            check(network.requests.size() == sent + 8, "next equipment resumes automation in the same GUI, round " + round);
            step(15, false);
            check(network.requests.size() == sent + 8, "new-round batch waits for confirmation");
            apply();
            for (int elapsed = 1; elapsed <= 400; elapsed++) {
                step(1, true);
                tailorServer.func_145845_h();
                // Original container synchronizes only time; do not invent full tile updates each tick.
                container.func_75137_b(0, tailorServer.time);
                check(tailorServer.isStart && tailorServer.time == elapsed, "consecutive craft preserves original timer");
            }
            check(tailorServer.point > 1000, "each consecutive craft runs beyond 1000 points");
            step(1, false); // A final batch reaches the server after completion and is ignored there.
            tailorServer.func_145845_h();
            apply();
            check(!tailorServer.isStart && tailorServer.time == 0, "server finishes consecutive recipe");
            ItemStack output = tailorServer.func_70301_a(5);
            check(output != null && output.func_77973_b() == TextileCore.MagicItemTextile1
                    && output.func_77960_j() == round % 3 && output.func_77978_p().func_74762_e("gamepoint") == 200,
                    "real output and original score settlement");
            // Exercise timer and output arriving independently, as separate vanilla packets do.
            if (round != 1) tailor.func_70299_a(5, output.func_77946_l());
            if (round != 3) container.func_75137_b(0, 0);
            check(tailor.isStart, "reproduce original stale isStart after timer-only completion update");
            sent = network.requests.size();
            // Alternate immediate next start (no idle client tick) and a pause longer than the old timeout.
            if (round % 2 != 0) {
                step(240, true);
                check(network.requests.size() == sent, "completed recipe never sends stale-board clicks");
                check(auto().field_146124_l && auto().field_146126_j.contains("\u00a7a"),
                        "timer-only completion preserves automation without waiting-sync timeout");
            }
            check(starts == round + 1, "only manual recipe starts were sent");
        }
        report.add("PASS: six consecutive real textile recipes in one GUI; all three output types; independently delayed timer/output updates with stale isStart, ignored final batches, immediate restarts and 240-tick pauses, delayed new-round replies, unchanged score settlement");
    }

    private void testTailor() throws Exception {
        fresh();
        tailor = new TileEntityClothesTailor(); attach(tailor);
        tailorServer = new TileEntityClothesTailor(); attach(tailorServer);
        show(new GuiClothesTailor(mc.field_71439_g.field_71071_by, tailor));
        layout(2, "tailor.png");
        click(auto()); step(5, false);
        check(network.requests.isEmpty(), "tailoring waits for manual start");
        tailorServer.setStart(); sync(tailorServer, tailor);
        step(1, false); int count = network.requests.size();
        check(count == 8, "bounded tailoring batch");
        step(15, false); check(network.requests.size() == count, "no repeat before confirmation");
        apply();
        step(180, true);
        check(tailor.point > 200, "tailoring continues beyond the old 200-point limit");
        count = network.requests.size(); step(20, true);
        check(network.requests.size() > count, "active round keeps sending after 200 points");
        check(starts == 0, "no automatic restart");
        render("tailor-auto.png");
        apply();
        // Run the real tile's server-side timer in an isolated in-memory world.
        WorldClient timerWorld = new WorldClient(network, new WorldSettings(0, WorldSettings.GameType.SURVIVAL,
                false, false, WorldType.field_77138_c), 0, EnumDifficulty.PEACEFUL, mc.field_71424_I);
        ReflectionHelper.findField(World.class, "field_72995_K", "isRemote").setBoolean(timerWorld, false);
        tailorServer.func_145834_a(timerWorld);
        for (int elapsed = 1; elapsed <= 400; elapsed++) {
            step(1, true);
            tailorServer.func_145845_h();
            sync(tailorServer, tailor);
            check(tailor.isStart && tailor.time == elapsed, "original timer keeps control until completion");
        }
        check(tailor.point > 1000, "no replacement score cap at 1000");
        check(tailor.time == 400, "automation did not reset or extend the timer");
        tailorServer.func_145845_h(); sync(tailorServer, tailor);
        check(!tailor.isStart, "real server timer completes the round");
        count = network.requests.size(); step(210, true);
        check(network.requests.size() == count, "completed timer stops new requests with GUI still open");
        check(auto().field_146124_l && starts == 0, "completion clears pending wait and never starts another recipe");
        tailorServer.func_145834_a(mc.field_71441_e);
        tailorServer.setStart(); sync(tailorServer, tailor); step(1, false);
        click(auto()); count = network.requests.size(); apply(); step(10, true);
        check(network.requests.size() == count, "stop does not queue another batch");
        click(auto()); step(1, false); count = network.requests.size(); step(205, false);
        check(network.requests.size() == count && !auto().field_146124_l, "timeout disables resend");
        apply(); step(2, false); check(auto().field_146124_l, "late reply releases barrier");
        check(!auto().field_146126_j.contains("\u00a7a"), "late reply never resumes");
        mc.func_147108_a(null); step(2, true);
        show(new GuiClothesTailor(mc.field_71439_g.field_71071_by, tailor));
        count = network.requests.size(); step(4, true);
        check(network.requests.size() == count, "reopening defaults off");
        report.add("PASS: tailoring beyond 200 and 1000 points until the real server timer ends; no timer extension or automatic restart; delayed acknowledgements, bounded batches, stop, timeout, late reply, close/reopen");
    }

    private void testPotion() throws Exception {
        fresh();
        potion = new TileEntityPotionMake(); attach(potion);
        potionServer = new TileEntityPotionMake(); attach(potionServer);
        show(new GuiPotionMake(new ContainerPotionMake(mc.field_71439_g.field_71071_by, potion)));
        layout(2, "potion.png"); click(auto()); step(5, true);
        check(network.requests.isEmpty(), "potion waits for start");
        potionServer.isStart = true; potionServer.LV = 1; potionServer.newGameLV(); sync(potionServer, potion);
        step(1, false); check(network.requests.size() == 4, "four pairs per batch");
        step(8, false); check(network.requests.size() == 4, "no stale pairing during delay"); apply();
        step(150, true);
        check(potion.LV == 4 && potion.point == 64 && potion.isAllZero(), "all four potion rounds cleared");
        check(network.requests.size() == 64, "exactly 64 valid matches");
        render("potion-auto.png");
        step(10, true); check(network.requests.size() == 64, "empty board waits for vanilla timer");
        check(starts == 0, "potion never consumes another recipe automatically");
        report.add("PASS: potion all 4 rounds, exactly 64 valid distinct pairs, delayed sync, no duplicate packets, vanilla timer retained");
    }

    private TileEntityCookingTable cookingTile() {
        TileEntityCookingTable tile = new TileEntityCookingTable(); attach(tile);
        for (int i = 0; i < 7; i++) tile.func_70299_a(i, new ItemStack(Items.field_151034_e));
        return tile;
    }
    private void testCooking() throws Exception {
        fresh(); TileEntityCookingTable tile = cookingTile();
        show(new GuiCookingTable(mc.field_71439_g.field_71071_by, tile));
        layout(2, "cooking-table.png"); click(auto()); step(3, true);
        check(network.requests.isEmpty(), "table auto only arms");
        click(original()); apply(); check(starts == 1, "original start sends exactly one packet");
        GuiCuisine gui = new GuiCuisine(tile); gui.rand.setSeed(123); show(gui);
        check(auto().field_146126_j.contains("\u00a7a"), "auto follows requested cooking transition");
        layout(2, "cuisine.png");
        gui.foods.clear(); gui.foods.add(new FoodGame(0, false, 15, 175, 2));
        gui.foods.add(new FoodGame(1, true, 120, 175, 2));
        step(1, true); check(gui.point == 20, "actual collision catches good food");
        check(gui.foods.size() == 1 && gui.foods.get(0).isBad, "bad food avoided");
        int time = gui.maxGameTime, score = gui.point;
        MinigameCuisineAccess.steer(gui);
        check(gui.maxGameTime == time && gui.point == score, "steering never changes score or timer");
        step(70, true); render("cuisine-auto.png"); step(540, true);
        check(submissions == 1 && gui.point > 100, "cooking succeeds and submits exactly once");
        step(10, true); check(submissions == 1, "no repeated result");
        int lowest = Integer.MAX_VALUE;
        for (int seed = 0; seed < 40; seed++) {
            gui = new GuiCuisine(tile); gui.rand.setSeed(seed); show(gui); click(auto());
            step(601, true); lowest = Math.min(lowest, gui.point);
            check(gui.point > 100, "cooking succeeds seed " + seed);
        }
        show(new GuiCookingTable(mc.field_71439_g.field_71071_by, tile)); click(auto());
        gui = new GuiCuisine(tile); show(gui);
        check(!auto().field_146126_j.contains("\u00a7a"), "unrequested transition is not armed");
        click(auto()); step(10, true); click(auto());
        gui.foods.clear(); gui.maxGameTime = 1; step(5, true);
        int submitted = submissions; step(5, true);
        check(submissions == submitted && mc.field_71462_r == gui, "off leaves manual submit available");
        report.add("PASS: cooking start handoff, natural 600-tick game, real collisions, bad-food avoidance, 40 seeded games above success threshold (minimum " + lowest + "), exactly one submit, manual off");
    }

    private void testAnvil() throws Exception {
        for (int run = 0; run < 18; run++) {
            fresh(); anvil = new TileEntityCastingOther(); attach(anvil);
            ItemStack piece = ItemCasting.getItemCasting(new ItemStack(run % 2 == 0 ? Items.field_151040_l : Items.field_151042_j), 1, 0, 1, false, 0);
            piece.func_77978_p().func_74768_a("temperature", 10);
            piece.func_77978_p().func_74768_a("forge", run == 0 ? 0 : run == 1 ? 120 : (run * 23) % 121);
            anvil.func_70299_a(0, piece); anvil.func_70299_a(1, new ItemStack(CastingCore.ItemCastingHamm));
            GuiCastingAnvil gui = new GuiCastingAnvil(new ContainerCastingOther(mc.field_71439_g.field_71071_by, anvil, 0), anvil);
            show(gui);
            if (run == 0) layout(1, "anvil.png");
            click(auto()); step(1, false);
            check(network.requests.size() == 1, "one anvil action in flight");
            step(8, false); check(network.requests.size() == 1, "anvil waits for sync");
            apply();
            if (run == 0) { step(1, true); render("anvil-auto.png"); }
            step(190, true);
            check(completions == 1 && completionValid, "original completion validates target and last seven " + run);
            check(anvil.func_70301_a(0) == null, "forged workpiece consumed by simulated server");
            int count = network.requests.size(); step(5, true);
            check(network.requests.size() == count, "anvil has no extra finish or hit");
        }
        fresh(); anvil = new TileEntityCastingOther(); attach(anvil);
        ItemStack piece = ItemCasting.getItemCasting(new ItemStack(Items.field_151040_l), 1, 0, 1, false, 0);
        anvil.func_70299_a(0, piece); anvil.func_70299_a(1, new ItemStack(CastingCore.ItemCastingHamm));
        show(new GuiCastingAnvil(new ContainerCastingOther(mc.field_71439_g.field_71071_by, anvil, 0), anvil));
        click(auto()); step(10, true); check(network.requests.isEmpty(), "cold anvil waits");
        piece.func_77978_p().func_74768_a("temperature", 10); piece.func_77978_p().func_74768_a("use", 1);
        step(3, true); check(network.requests.isEmpty(), "insufficient workpiece durability stops safely");
        piece.func_77978_p().func_74768_a("use", 400); click(auto()); step(1, false);
        int sent = network.requests.size(); click(auto()); step(3, false);
        check(network.requests.size() == sent, "stopping anvil retains pending barrier");
        apply(); step(1, false);
        piece.func_77978_p().func_74768_a("forge", (piece.func_77978_p().func_74762_e("forge") + 1) % 121);
        click(auto()); step(1, false);
        check(network.requests.size() == sent && !auto().field_146126_j.contains("\u00a7a"), "manual bar change invalidates old plan");
        report.add("PASS: anvil 18 full plans with actual GUI completion, boundary positions, delayed acknowledgements, cold workpiece and durability guard");
    }

    private void fresh() {
        if (mc.field_71462_r != null) mc.func_147108_a(null);
        network = new RecordingHandler(mc);
        WorldClient world = new WorldClient(network, new WorldSettings(0, WorldSettings.GameType.SURVIVAL,
                false, false, WorldType.field_77138_c), 0, EnumDifficulty.PEACEFUL, mc.field_71424_I);
        mc.field_71441_e = world;
        mc.field_71439_g = new EntityClientPlayerMP(mc, world, mc.func_110432_I(), network, new StatFileWriter());
        mc.field_71442_b = new PlayerControllerMP(mc, network);
        tailor = null; potion = null; anvil = null;
        processed = completions = submissions = starts = 0; completionValid = false;
    }
    private void attach(TileEntity tile) { tile.func_145834_a(mc.field_71441_e); tile.field_145851_c=0; tile.field_145848_d=64; tile.field_145849_e=0; }
    private void sync(TileEntity from, TileEntity to) { NBTTagCompound tag = new NBTTagCompound(); from.func_145841_b(tag); to.func_145839_a(tag); }
    private void show(GuiScreen gui) { mc.func_147108_a(gui); }
    @SuppressWarnings("unchecked") private List<GuiButton> buttons() throws Exception { return (List<GuiButton>)buttonList.get(mc.field_71462_r); }
    private GuiButton auto() throws Exception { for (GuiButton b : buttons()) if (b.field_146127_k == 27960) return b; throw new AssertionError("Missing auto"); }
    private GuiButton original() throws Exception { for (GuiButton b : buttons()) if (b.field_146127_k == 0) return b; throw new AssertionError("Missing original"); }
    private void click(GuiButton button) throws Exception { mouse.invoke(mc.field_71462_r, button.field_146128_h+2, button.field_146129_i+2, 0); }
    private void step(int ticks, boolean replies) {
        for (int i=0; i<ticks; i++) {
            FMLCommonHandler.instance().bus().post(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
            if (mc.field_71462_r != null) mc.field_71462_r.func_73876_c();
            if (replies && i % 3 == 0) apply();
        }
    }
    private void apply() {
        while (processed < network.requests.size()) {
            Request r = network.requests.get(processed++);
            if (r.channel == 78 && r.id == 1) {
                if (!tailorServer.isStart) continue; // Original handler ignores clicks after completion.
                check(tailorServer.data[r.a] != 0, "tailor never clicks an empty cell");
                tailorServer.set(r.a); sync(tailorServer, tailor);
            } else if (r.channel == 78 && r.id == 0) {
                starts++;
                if (tailorServer.canStart()) { tailorServer.setStart(); sync(tailorServer, tailor); }
            } else if (r.channel == 21 && r.id == 2) {
                check(r.a != r.b && potionServer.trypushCard(r.a, r.b), "valid potion pair"); sync(potionServer, potion);
            } else if (r.channel == 6 && r.id == 1) {
                ItemStack p = anvil.func_70301_a(0), h = anvil.func_70301_a(1);
                int[] delta = {-12,-9,-6,-3,2,4,6,8}, wear = {6,4,3,2,2,3,4,6};
                check(p != null && h != null && p.func_77978_p().func_74762_e("temperature") > 0, "anvil prerequisites");
                int f = Math.max(0, Math.min(120, p.func_77978_p().func_74762_e("forge") + delta[r.a]));
                p.func_77978_p().func_74768_a("forge", f);
                p.func_77978_p().func_74768_a("use", p.func_77978_p().func_74762_e("use") - wear[r.a]);
                h.func_77964_b(h.func_77960_j()+3);
            } else if (r.channel == 6 && r.id == 2) {
                completions++;
                ItemStack p = anvil.func_70301_a(0);
                Random random = new Random(ItemCasting.getSeed1(p));
                int target = 40 + random.nextInt(70);
                int[] need = {target % 7, random.nextInt(8), 0};
                random.setSeed(ItemCasting.getSeed2(p)); need[2] = random.nextInt(8);
                int[] hits = new int[8]; int n = 0;
                for (int j=processed-2; j>=0 && n<7; j--) {
                    Request hit = network.requests.get(j);
                    if (hit.channel==6 && hit.id==1) { hits[hit.a]++; n++; }
                }
                completionValid = n==7 && p.func_77978_p().func_74762_e("forge")==target;
                for (int a : need) if (--hits[a] < 0) completionValid=false;
                anvil.func_70299_a(0, null);
            } else if (r.channel == 63 && r.id == 2) { submissions++; check(r.a > 100, "submitted cooking succeeds"); }
            else starts++;
        }
    }
    private void layout(int expected, String screenshot) throws Exception {
        check(buttons().size()==expected, "original buttons preserved with one auto");
        GuiScreen gui=mc.field_71462_r; gui.func_146280_a(mc,320,240);
        check(buttons().size()==expected, "resize does not duplicate");
        GuiButton b=auto(); check(b.field_146128_h>=0 && b.field_146128_h+b.field_146120_f<=320
                && b.field_146129_i>=0 && b.field_146129_i+b.field_146121_g<=240, "button in small screen");
        for (GuiButton other : buttons()) if (other != b)
            check(!overlap(b, other.field_146128_h, other.field_146129_i, other.field_146120_f, other.field_146121_g), "auto does not cover original button");
        if (gui instanceof GuiContainer) {
            int left = ReflectionHelper.findField(GuiContainer.class, "field_147003_i", "guiLeft").getInt(gui);
            int top = ReflectionHelper.findField(GuiContainer.class, "field_147009_r", "guiTop").getInt(gui);
            for (Object value : ((GuiContainer)gui).field_147002_h.field_75151_b) {
                Slot slot = (Slot)value;
                check(!overlap(b, left+slot.field_75223_e-1, top+slot.field_75221_f-1, 18, 18), "auto leaves every inventory slot accessible");
            }
        }
        gui.func_146280_a(mc,640,360); render(screenshot);
    }
    private boolean overlap(GuiButton b, int x, int y, int w, int h) {
        return b.field_146128_h < x+w && b.field_146128_h+b.field_146120_f > x
                && b.field_146129_i < y+h && b.field_146129_i+b.field_146121_g > y;
    }
    private void render(String filename) throws Exception {
        GuiScreen gui=mc.field_71462_r;
        mc.func_147110_a().func_147610_a(true);
        GL11.glClearColor(.09F,.105F,.13F,1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0,gui.field_146294_l,gui.field_146295_m,0,1000,3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity(); GL11.glTranslatef(0,0,-2000);
        GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glEnable(GL11.GL_TEXTURE_2D);
        gui.func_73863_a(0,0,0);
        ScreenShotHelper.func_148259_a(fixture.toFile(),filename,mc.field_71443_c,mc.field_71440_d,mc.func_147110_a());
    }
    private void check(boolean pass,String text) { checks++; if(!pass) throw new AssertionError(text); }
    private static final class Request {
        int channel,id,a,b;
        Request(ByteBuf buf) {
            channel=buf.readUnsignedByte(); id=buf.readInt();
            if(channel==6) { a=buf.readInt(); buf.skipBytes(12); }
            else { buf.skipBytes(12); if(channel==21) {buf.readBoolean(); a=buf.readInt(); b=buf.readInt();} else a=buf.readInt(); }
        }
    }
    public static final class RecordingHandler extends NetHandlerPlayClient {
        final List<Request> requests = new ArrayList<Request>();
        RecordingHandler(Minecraft mc) { super(mc,null,local.foxfoxvalidation.MergedIdentity.offlineNetwork()); }
        @Override public void func_147297_a(Packet packet) {
            if(packet instanceof FMLProxyPacket) {
                ByteBuf buf=((FMLProxyPacket)packet).payload().duplicate();
                if(buf.readableBytes()<21) return;
                int code=buf.getUnsignedByte(buf.readerIndex());
                if(code==6||code==21||code==63||code==78) requests.add(new Request(buf));
            }
        }
    }
}
