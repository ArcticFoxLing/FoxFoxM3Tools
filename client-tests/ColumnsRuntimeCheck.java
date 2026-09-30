package local.tooltipcolumnstest;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.NetworkModHolder;
import cpw.mods.fml.relauncher.Side;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import local.foxfoxm3tools.tooltip.TooltipSettings;
import local.foxfoxm3tools.tooltip.client.ColumnRenderer;
import local.foxfoxm3tools.tooltip.layout.ColumnLayout;
import local.foxfoxm3tools.tooltip.layout.ColumnLayout.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.network.NetworkManager;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.world.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ScreenShotHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import project.studio.manametalmod.api.Quality;
import project.studio.manametalmod.tooltip.*;
import project.studio.manametalmod.event.EventGUI;
import project.studio.manametalmod.nei.NEITooltipHandlerM3;

@Mod(modid="tooltipcolumnstest", name="Isolated Tooltip Columns Test", version="1",
        dependencies="required-after:manametalmod;required-after:foxfoxm3tools;after:NotEnoughItems")
public final class ColumnsRuntimeCheck {
    private boolean done;
    private int checks;
    private final List<String> report = new ArrayList<String>();
    private Minecraft mc;
    private RecordingFont font;
    private Path root;
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance().bus().register(this);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) throws Exception {
        mc = Minecraft.func_71410_x();
        if (done || event.phase != TickEvent.Phase.END || !(mc.field_71462_r instanceof GuiMainMenu)) return;
        done = true;
        root = Paths.get(System.getProperty("tooltipcolumns.test.root"));
        if (!mc.field_71412_D.getCanonicalFile().equals(root.resolve("client").toFile().getCanonicalFile()))
            throw new IllegalStateException("Wrong game directory");
        try {
            local.foxfoxvalidation.MergedIdentity.verify();
            check(mc.field_71441_e == null && mc.field_71439_g == null, "No world or remote server entered");
            check(Loader.isModLoaded("foxfoxm3tools"), "Release mod loaded by Forge");
            ModContainer mod = Loader.instance().getIndexedModList().get("foxfoxm3tools");
            NetworkModHolder holder = NetworkRegistry.INSTANCE.registry().get(mod);
            check(holder != null && holder.check(Collections.<String,String>emptyMap(), Side.SERVER), "Server does not need addon");
            font = new RecordingFont(mc);
            ItemStack item = new ItemStack(Items.field_151048_u); // diamond sword: real textured item rendering
            List<String> example = example();
            GuiScreen gui = screen(640, 360);
            Layout layout = layout(example, Collections.<List<String>>emptyList(), gui);
            check(layout.panels.size() == 2 && layout.pages == 1, "Example fits two adjacent panels");
            canvas(gui);
            boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            int matrixDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
            int attribDepth = GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH);
            float[] matrix = matrix();
            ToolTipM3.itemRender.field_77023_b = 123;
            // This calls the actual transformed release method, not the helper directly.
            ToolTipM3.drawToolTIP(example, 320, 170, font, gui, item, Quality.Junk);
            check(ToolTipM3.itemRender.field_77023_b == 123, "Item renderer depth restored");
            check(lighting == GL11.glIsEnabled(GL11.GL_LIGHTING) && depth == GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "GL enable state restored");
            check(matrixDepth == GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH)
                    && attribDepth == GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH), "GL stack depths restored");
            check(Arrays.equals(matrix, matrix()), "GL transform restored");
            for (Panel panel : layout.panels) for (Row row : panel.rows) if (row.kind == ColumnLayout.TEXT)
                check(font.drawn.contains(row.text), "Actual draw includes: " + row.text);
            check(font.drawn.contains("\u00a772 / 2"), "Continuation frame was actually rendered by hook");
            screenshot("two-columns.png");
            report.add("PASS real transformed ManaMetal 8.0.7 renderer: two columns, Chinese/color text, textured item, state restoration");

            List<String> shortTip = Arrays.asList("Short item", "A description");
            check(!ColumnRenderer.drawIfOverflow(shortTip, 30, 30, font, gui, item, Quality.Normal), "Fitting tooltip uses original");
            TooltipSettings.enabled = false;
            check(!ColumnRenderer.drawIfOverflow(example, 320, 170, font, gui, item, Quality.Junk), "Config disables hook");
            TooltipSettings.enabled = true;
            check(!ColumnRenderer.drawIfOverflow(Collections.<String>emptyList(), 0, 0, font, gui, item, Quality.Junk), "Empty tooltip safe");
            report.add("PASS fitting/empty tooltips and config switch");

            List<String> longTip = new ArrayList<String>(example);
            for (int i = 0; i < 80; i++) longTip.add("\u00a7a附加属性 " + i + "：最终伤害 +123%");
            GuiScreen small = screen(320, 240);
            Layout pages = layout(longTip, Collections.<List<String>>emptyList(), small);
            check(pages.pages > 1, "Very long tooltips page when horizontal space is full");
            List<String> seen = new ArrayList<String>();
            for (int p = 0; p < pages.pages; p++) {
                canvas(small);
                ColumnRenderer.draw(pages, p, 319, 239, font, small, item, Quality.Legend);
                seen.addAll(font.drawn);
                if (p == 0 || p == pages.pages - 1) screenshot("small-page-" + (p + 1) + ".png");
            }
            for (Panel panel : pages.panels) for (Row row : panel.rows) if (row.kind == ColumnLayout.TEXT)
                check(seen.contains(row.text), "Paged attribute remains accessible: " + row.text);
            report.add("PASS 320x240 GUI: " + pages.pages + " pages, every attribute rendered including final attribute");

            ItemStack special = new ItemStack(new ExtraItem());
            List<List<String>> extra = ColumnRenderer.extraText(special);
            check(extra.size() == 2, "Custom extra-text provider retained");
            List<String> main = Arrays.asList("\u00a76附加说明测试", "\u00a7f主属性");
            Layout extraLayout = layout(main, extra, gui);
            canvas(gui);
            ToolTipM3.drawToolTIP(main, 600, 350, font, gui, special, Quality.Chaos);
            for (Panel panel : extraLayout.panels) for (Row row : panel.rows) if (row.kind == ColumnLayout.TEXT)
                check(font.drawn.contains(row.text), "Extra provider text rendered: " + row.text);
            screenshot("extra-descriptions.png");
            ItemStack socketed = new ItemStack(Items.field_151048_u);
            NBTTagCompound nbt = new NBTTagCompound(), strengthen = new NBTTagCompound(), gem = new NBTTagCompound(), tag = new NBTTagCompound();
            tag.func_74768_a("keyEnchantmentGemSpecialEffect", 1);
            tag.func_74768_a("keyEnchantmentGemTier", 1);
            gem.func_74782_a("tag", tag); gem.func_74777_a("Damage", (short) 0); gem.func_74778_a("LTK", "TestGem");
            NBTTagList gems = new NBTTagList(); gems.func_74742_a(gem);
            strengthen.func_74782_a("inlay_gems", gems); nbt.func_74782_a("weapon_strengthen", strengthen); socketed.func_77982_d(nbt);
            check(ColumnRenderer.extraText(socketed).size() == 1, "Socketed gem effects retained from actual NBT");
            tag.func_74768_a("keyEnchantmentGemTier", -1);
            check(ColumnRenderer.extraText(socketed).isEmpty(), "Invalid gem data guarded");
            report.add("PASS custom extra descriptions, socketed gem NBT, corrupted gem bounds");
            integration();
            check(GL11.glGetError() == GL11.GL_NO_ERROR, "No GL error after all rendering");
            report.add("PASS " + checks + " runtime assertions");
            report.add("LIMIT: isolated real Forge client with controlled tooltip lists; no original remote-server item replay.");
            report.add("status=PASS");
        } catch (Throwable failure) {
            StringWriter trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace)); report.add("status=FAIL\n" + trace);
        } finally {
            if (font != null) mc.field_71466_p = font.delegate;
            mc.field_71439_g = null; mc.field_71441_e = null; mc.field_71442_b = null;
            Files.write(root.resolve("result.txt"), report, StandardCharsets.UTF_8);
            for (String line : report) System.out.println("[TooltipColumnsTest] " + line);
            mc.func_71400_g();
        }
    }
    private void integration() {
        NetHandlerPlayClient network = new NetHandlerPlayClient(mc, null, local.foxfoxvalidation.MergedIdentity.offlineNetwork());
        WorldClient world = new WorldClient(network,
                new WorldSettings(0, WorldSettings.GameType.SURVIVAL, false, false, WorldType.field_77138_c),
                0, EnumDifficulty.PEACEFUL, mc.field_71424_I);
        EntityClientPlayerMP player = new EntityClientPlayerMP(mc, world, mc.func_110432_I(), network, new StatFileWriter());
        mc.field_71441_e = world; mc.field_71439_g = player; mc.field_71466_p = font;
        mc.field_71442_b = new PlayerControllerMP(mc, network);
        ItemStack stack = new ItemStack(Items.field_151048_u);
        NBTTagCompound nbt = new NBTTagCompound(), display = new NBTTagCompound();
        NBTTagList lore = new NBTTagList();
        for (int i = 0; i < 40; i++) lore.func_74742_a(new NBTTagString("\u00a7bTEST-LORE-" + i));
        display.func_74782_a("Lore", lore); nbt.func_74782_a("display", display); stack.func_77982_d(nbt);
        GuiInventory inventory = new GuiInventory(player); inventory.func_146280_a(mc, 640, 360);
        canvas(inventory);
        EventGUI.renderToolTip(stack, 320, 170, inventory);
        check(containsText("TEST-LORE-0") && containsText("TEST-LORE-39"), "Inventory full tooltip pipeline retains all lore");
        List<String> inventoryDraw = new ArrayList<String>(font.drawn);
        canvas(inventory);
        List<String> neiTip = new ArrayList<String>(Arrays.asList("NEI original placeholder"));
        new NEITooltipHandlerM3().handleItemTooltip(inventory, stack, 320, 170, neiTip);
        check(neiTip.isEmpty(), "ManaMetal NEI handler suppresses duplicate vanilla tooltip");
        check(inventoryDraw.equals(font.drawn), "NEI tooltip path renders same complete column content");
        screenshot("nei-item-lore.png");
        mc.field_71466_p = font.delegate; mc.field_71439_g = null; mc.field_71441_e = null; mc.field_71442_b = null;
        report.add("PASS actual ItemStack lore -> Forge tooltip events -> ManaMetal renderer, both inventory and NEI handler paths");
    }
    private boolean containsText(String text) {
        for (String line : font.drawn) if (line.contains(text)) return true;
        return false;
    }
    private GuiScreen screen(int width, int height) {
        GuiScreen screen = new GuiScreen(); screen.func_146280_a(mc, width, height); return screen;
    }
    private Layout layout(List<String> main, List<List<String>> extras, GuiScreen gui) {
        return ColumnLayout.create(main, extras, new Metrics() { public int width(String text) { return font.func_78256_a(text); } },
                gui.field_146294_l, gui.field_146295_m, 240, 6, 8, font.field_78288_b);
    }
    private void canvas(GuiScreen gui) {
        mc.func_147110_a().func_147610_a(true);
        GL11.glClearColor(.09F, .105F, .13F, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity();
        GL11.glOrtho(0, gui.field_146294_l, gui.field_146295_m, 0, 1000, 3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity(); GL11.glTranslatef(0, 0, -2000);
        GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glEnable(GL11.GL_TEXTURE_2D);
        font.drawn.clear();
    }
    private void screenshot(String name) {
        ScreenShotHelper.func_148259_a(root.toFile(), name, mc.field_71443_c, mc.field_71440_d, mc.func_147110_a());
    }
    private float[] matrix() {
        FloatBuffer values = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, values);
        float[] result = new float[16]; values.get(result); return result;
    }
    private void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    private List<String> example() {
        return Arrays.asList("\u00a7f分栏测试武器", "\u00a77垃圾( X 1)", "\u00a77武器", "[M3LINE]",
                "\u00a77锋利 IX", "\u00a77暴怒 IX", "\u00a77抢夺 VII", "\u00a7e长按攻击键可连续攻击", "\u00a7d等级 3 传说武器",
                "\u00a7d吸取伤害的 4% 恢复生命力，提升 6% 移动速度", "\u00a7d提升 15% 造成的伤害，穿透值提升 3 点", "[M3LINE]",
                "\u00a7f采收等级：20", "\u00a7f采收效率：60", "\u00a7f物品耐久：10 / 10", "[M3LINE]",
                "\u00a7f需求等级：\u00a7a50", "\u00a7f物理伤害：+180", "\u00a7f(180 \u00a7b+ \u00a7d0 \u00a7b+ 0 + \u00a760 \u00a7b+ \u00a7a0\u00a7f)",
                "\u00a7f移动速度：+16", "[M3LINE]", "\u00a7f强化卷数：0 / 0", "\u00a7f融合星数：0 / 0", "\u00a7f物品重量：4.0", "\u00a7f物品价值：2000",
                "[M3LINE]", "\u00a76可强化", "\u00a76+6", "\u00a7b全能攻击力：+923", "\u00a7b穿透值：+35", "\u00a7b暴击伤害：+208%",
                "\u00a7b造成的伤害：+35%", "\u00a7b对魔王造成的伤害：+46%", "\u00a7b对普通怪物造成的伤害：+35%", "\u00a7b对魔王最终伤害：+12%",
                "\u00a7b对普通怪物最终伤害：+9%", "\u00a7b最终伤害：+7%", "\u00a7b职业增幅：+7%", "\u00a79manametalmod");
    }
    public static final class RecordingFont extends FontRenderer {
        final FontRenderer delegate;
        final List<String> drawn = new ArrayList<String>();
        RecordingFont(Minecraft mc) {
            super(mc.field_71474_y, new ResourceLocation("textures/font/ascii.png"), mc.func_110434_K(), true);
            delegate = mc.field_71466_p;
        }
        @Override public int func_78256_a(String text) { return delegate == null ? super.func_78256_a(text) : delegate.func_78256_a(text); }
        @Override public List func_78271_c(String text, int width) { return delegate.func_78271_c(text, width); }
        @Override public int func_78261_a(String text, int x, int y, int color) {
            drawn.add(text); return delegate.func_78261_a(text, x, y, color);
        }
    }
    public static final class ExtraItem extends Item implements IItemToolTipExtraTextRender {
        @SuppressWarnings("unchecked") public List<String>[] getRenderTextObject(ItemStack item) {
            List<String> group = new ArrayList<String>();
            group.add("\u00a76宝石与额外说明");
            for (int i = 0; i < 28; i++) group.add("\u00a7a宝石效果 " + i + "：造成的伤害 +10%");
            return new List[] {group, Arrays.asList("\u00a7b最后一组附加说明", "\u00a7d最后一条属性完整显示")};
        }
    }
}
