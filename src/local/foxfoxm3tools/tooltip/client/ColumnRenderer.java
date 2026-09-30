package local.foxfoxm3tools.tooltip.client;

import java.util.*;
import local.foxfoxm3tools.tooltip.TooltipSettings;
import local.foxfoxm3tools.tooltip.layout.ColumnLayout;
import local.foxfoxm3tools.tooltip.layout.ColumnLayout.*;
import local.foxfoxm3tools.tooltip.layout.PageState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import project.studio.manametalmod.MMM;
import project.studio.manametalmod.api.Quality;
import project.studio.manametalmod.client.GuiUtilsM3;
import project.studio.manametalmod.produce.gemcraft.*;
import project.studio.manametalmod.tooltip.*;

public final class ColumnRenderer {
    private static final PageState PAGES = new PageState();
    private static ItemStack pageItem;

    public static boolean drawIfOverflow(List<String> lines, int mouseX, int mouseY,
            final FontRenderer font, GuiScreen gui, ItemStack item, Quality quality) {
        if (!TooltipSettings.enabled || lines == null || lines.isEmpty() || font == null
                || gui == null || item == null) return false;
        int margin = TooltipSettings.screenMargin;
        if (gui.field_146294_l - margin * 2 < 40 || gui.field_146295_m - margin * 2 < 64) return false;
        List<List<String>> extras = extraText(item);
        if (originalFits(lines, extras, font, gui, mouseX, mouseY, margin)) return false;
        Layout layout = ColumnLayout.create(lines, extras, new Metrics() {
            public int width(String text) { return font.func_78256_a(text); }
        }, gui.field_146294_l, gui.field_146295_m, TooltipSettings.maxColumnWidth,
                margin, TooltipSettings.columnGap, font.field_78288_b);
        // NEI may hand out an equivalent ItemStack copy each frame.
        if (!ItemStack.func_77989_b(item, pageItem)) pageItem = item.func_77946_l();
        int page = PAGES.update(pageItem, gui, gui.field_146294_l, gui.field_146295_m, layout.pages,
                System.nanoTime() / 1000000L, Keyboard.isKeyDown(Keyboard.KEY_PRIOR),
                Keyboard.isKeyDown(Keyboard.KEY_NEXT));
        draw(layout, page, mouseX, mouseY, font, gui, item, quality);
        return true;
    }

    /** Reproduce the old bounds, including the separate gem panel, before deciding to intervene. */
    public static boolean originalFits(List<String> lines, List<List<String>> extras, FontRenderer font,
            GuiScreen gui, int mouseX, int mouseY, int margin) {
        int width = 0;
        for (String text : lines) if (text != null) width = Math.max(width, font.func_78256_a(text));
        width = Math.max(100, width + width % 2 + 8);
        long height = lines.size() == 1 ? 58L : 50L + lines.size() * 10L;
        int x = mouseX + 12;
        if (x + width > gui.field_146294_l) x -= 28 + width;
        long y = Math.min((long) mouseY + 4, gui.field_146295_m - height - 6);
        if (x - 3 < margin || x + width > gui.field_146294_l - margin || y - 4 < margin
                || y + height + 2 > gui.field_146295_m - margin) return false;
        if (!extras.isEmpty()) {
            int extraWidth = 0, extraHeight = 0;
            for (List<String> group : extras) {
                for (String text : group) {
                    extraWidth = Math.max(extraWidth, Math.min(170, font.func_78256_a(text)));
                    extraHeight += font.func_78271_c(text, 170).size() * 10;
                }
                extraHeight += 2;
            }
            if (x + width + 3 + extraWidth + 6 > gui.field_146294_l - margin
                    || extraHeight + 6 > gui.field_146295_m - margin * 2) return false;
        }
        return true;
    }

    /** Same sources as ToolTipM3: item-specific text first, otherwise socketed gem effects. */
    public static List<List<String>> extraText(ItemStack item) {
        List<List<String>> result = new ArrayList<List<String>>();
        if (item.func_77973_b() instanceof IItemToolTipExtraTextRender) {
            List<String>[] groups = ((IItemToolTipExtraTextRender) item.func_77973_b()).getRenderTextObject(item);
            if (groups != null) for (List<String> group : groups) {
                if (group == null) continue;
                List<String> copy = new ArrayList<String>();
                for (String text : group) if (text != null) copy.add(text);
                if (!copy.isEmpty()) result.add(copy);
            }
        } else if (item.func_77942_o() && item.func_77978_p().func_150297_b("weapon_strengthen", 10)) {
            NBTTagList gems = item.func_77978_p().func_74775_l("weapon_strengthen").func_150295_c("inlay_gems", 10);
            for (int i = 0; i < gems.func_74745_c(); i++) {
                NBTTagCompound gem = gems.func_150305_b(i);
                if (!gem.func_150297_b("tag", 10)) continue;
                NBTTagCompound tag = gem.func_74775_l("tag");
                int effect = tag.func_74762_e("keyEnchantmentGemSpecialEffect");
                int tier = tag.func_74762_e("keyEnchantmentGemTier");
                int metadata = gem.func_74765_d("Damage");
                if (effect <= 0 || effect >= EnchantmentGemSpecialEffect.values.length || tier < 0
                        || tier >= EnchantmentGemTier.tiers.length || metadata < 0
                        || metadata >= EnchantmentGemCore.gems.size()) continue;
                EnchantmentGemSpecialEffect type = EnchantmentGemSpecialEffect.values[effect];
                if (tier >= type.data.length) continue;
                EnchantmentGem definition = EnchantmentGemCore.gems.get(metadata);
                result.add(Arrays.asList("\u00a7f" + definition.gemIcon
                        + MMM.getTranslateText("ItemEnchantmentGem.LV." + tier)
                        + MMM.getTranslateText(gem.func_74779_i("LTK")),
                        "\u00a76" + MMM.getTranslateText("ItemEnchantmentGem.special.effect." + type.ordinal())
                        .replace("data", MMM.getFloat(type.data[tier] * 100F) + "%")));
            }
        }
        return result;
    }

    public static void draw(Layout layout, int page, int mouseX, int mouseY, FontRenderer font,
            GuiScreen gui, ItemStack item, Quality quality) {
        int main = 598, frame = 0xFFFFFF;
        if (quality != null && quality != Quality.Unknown && quality.ordinal() >= Quality.Legend.ordinal()) {
            main = 6818304; frame = 16771102;
        }
        if (quality != null && quality != Quality.Unknown && quality.ordinal() >= Quality.Chaos.ordinal())
            main = 8978582;
        int startX = layout.x(page, mouseX), startY = layout.y(page, mouseY);
        float oldZ = ToolTipM3.itemRender.field_77023_b;
        float oldBrightnessX = OpenGlHelper.lastBrightnessX, oldBrightnessY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            prepareText();
            for (int visible = 0; visible < layout.visibleCount(page); visible++) {
                int index = page * layout.perPage + visible;
                Panel panel = layout.panels.get(index);
                int x = startX + visible * (layout.panelWidth + layout.gap);
                frame(x, startY, layout.panelWidth, panel.height, main, frame);
                int y = startY + ColumnLayout.PAD;
                if (index > 0) {
                    center(font, "\u00a77" + (index + 1) + " / " + layout.panels.size(),
                            x, y, layout.panelWidth, false);
                    y += ColumnLayout.HEADER;
                }
                for (Row row : panel.rows) {
                    if (row.kind == ColumnLayout.SEPARATOR) divider(x, y, layout.panelWidth);
                    else if (row.kind == ColumnLayout.ICON) icon(x, y, layout.panelWidth, font, gui, item);
                    else center(font, row.text, x, y, layout.panelWidth, row.small);
                    y += row.height;
                }
            }
            if (layout.pages > 1) {
                int y = startY + layout.groupHeight(page) - layout.footer;
                frame(startX, y, layout.groupWidth(page), layout.footer, main, frame);
                center(font, "\u00a7e" + (page + 1) + "/" + layout.pages + "  PgUp / PgDn",
                        startX, y + 3, layout.groupWidth(page), false);
            }
        } finally {
            ToolTipM3.itemRender.field_77023_b = oldZ;
            OpenGlHelper.func_77475_a(OpenGlHelper.field_77476_b, oldBrightnessX, oldBrightnessY);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void prepareText() {
        RenderHelper.func_74518_a();
        GL11.glDisable(32826); // GL12.GL_RESCALE_NORMAL
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void frame(int x, int y, int width, int height, int main, int frame) {
        GuiUtilsM3.drawGradientRectVertical(x, y, width, height, main, 180, 512, 180, 300);
        GuiUtilsM3.drawGradientRectVertical(x + 2, y + 2, width - 4, height - 4, frame, 160, 0x4C4C4C, 160, 300);
        GuiUtilsM3.drawGradientRectVertical(x + 4, y + 4, width - 8, height - 8, main, 180, 512, 180, 300);
    }

    private static void center(FontRenderer font, String text, int x, int y, int width, boolean small) {
        GL11.glPushMatrix();
        try {
            float scale = small ? .9F : 1F;
            // Also keep the page hint readable and inside the panel on unusually narrow screens.
            int textWidth = font.func_78256_a(text);
            if (textWidth * scale > width - ColumnLayout.PAD * 2)
                scale = (float) (width - ColumnLayout.PAD * 2) / Math.max(1, textWidth);
            GL11.glTranslatef(x + (width - textWidth * scale) / 2F, y, 350);
            GL11.glScalef(scale, scale, 1);
            font.func_78261_a(text, 0, 0, -1);
        } finally { GL11.glPopMatrix(); }
    }

    private static void divider(int x, int y, int width) {
        int left = x + 10, half = (width - 26) / 2;
        GuiUtilsM3.drawGradientRectHorizontal(left, y + 3, half, 3, 0x727272, 150, 0xFFFFFF, 150, 350);
        GuiUtilsM3.drawGradientRectHorizontal(left + half + 6, y + 3, half, 3, 0xFFFFFF, 150, 0x727272, 150, 350);
        GuiUtilsM3.drawGradientRectHorizontal(x + width / 2 - 2, y + 2, 5, 5, 0x727272, 150, 0xFFFFFF, 150, 350);
    }

    private static void icon(int x, int y, int width, FontRenderer font, GuiScreen gui, ItemStack item) {
        int left = x + width / 2 - 18;
        GuiUtilsM3.drawGradientRectVertical(left, y + 2, 36, 36, 55551, 160, 0xFFFFFF, 160, 300);
        GuiUtilsM3.drawGradientRectVertical(left + 1, y + 3, 34, 34, 3343, 160, 0xFFFFFF, 160, 300);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(left + 2, y + 4, 0);
            GL11.glScalef(2, 2, 1);
            ToolTipM3.itemRender.field_77023_b = 400;
            RenderHelper.func_74520_c();
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            ToolTipM3.itemRender.func_82406_b(font, gui.field_146297_k.func_110434_K(), item, 0, 0);
            ToolTipM3.itemRender.func_77021_b(font, gui.field_146297_k.func_110434_K(), item, 0, 0);
            RenderHelper.func_74518_a();
            if (item.func_77973_b() instanceof IItemToolTipExtraRender)
                ToolTipExtraRender.render(Minecraft.func_71410_x().field_71439_g, item, IItemRenderer.ItemRenderType.INVENTORY);
        } finally {
            GL11.glPopMatrix(); GL11.glPopAttrib(); prepareText();
        }
    }
}
