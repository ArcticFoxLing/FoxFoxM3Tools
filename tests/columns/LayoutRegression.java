import java.util.*;
import local.foxfoxm3tools.tooltip.layout.ColumnLayout;
import local.foxfoxm3tools.tooltip.layout.ColumnLayout.*;
import local.foxfoxm3tools.tooltip.layout.PageState;

public final class LayoutRegression {
    private static int checks;
    private static final Metrics FONT = new Metrics() {
        public int width(String s) {
            int width = 0; boolean bold = false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\u00a7' && i + 1 < s.length()) {
                    char format = s.charAt(++i);
                    if (format == 'l') bold = true;
                    else if ("0123456789abcdefr".indexOf(format) >= 0) bold = false;
                } else width += (c > 127 ? 9 : 6) + (bold ? 1 : 0);
            }
            return width;
        }
    };
    public static void main(String[] args) {
        for (int count : new int[] {1, 12, 40, 90, 300})
            for (int w : new int[] {160, 208, 320, 427, 640, 960, 1920})
                for (int h : new int[] {100, 120, 240, 360, 540, 1080})
                    for (int fontHeight : new int[] {9, 16}) verify(count, w, h, fontHeight);
        List<String> bold = ColumnLayout.wrap("\u00a7b\u00a7lABCDEFGHIJ", 21, FONT);
        check(bold.size() > 1 && bold.get(1).startsWith("\u00a7b\u00a7l"), "Color and bold continue on wrap");
        List<String> reset = ColumnLayout.wrap("\u00a7cABC\u00a7rDEFGHIJ", 18, FONT);
        check(!reset.get(reset.size() - 1).contains("\u00a7c"), "Reset clears inherited color");
        check(ColumnLayout.wrap("A\n\nB", 20, FONT).equals(Arrays.asList("A", "", "B")), "Explicit empty lines");
        String giant = String.join("", Collections.nCopies(12000, "长"));
        List<String> wrapped = ColumnLayout.wrap(giant, 90, FONT);
        check(String.join("", wrapped).equals(giant), "Giant unbroken description neither lost nor recursive");
        PageState state = new PageState();
        Object a = new Object(), b = new Object(), gui = new Object();
        check(state.update(a, gui, 320, 240, 5, 1, false, false) == 0, "First page");
        check(state.update(a, gui, 320, 240, 5, 2, false, true) == 1, "PgDn advances");
        check(state.update(a, gui, 320, 240, 5, 3, false, true) == 1, "Held key does not repeat");
        state.update(a, gui, 320, 240, 5, 4, false, false);
        check(state.update(a, gui, 320, 240, 5, 5, true, false) == 0, "PgUp goes back");
        state.update(a, gui, 320, 240, 5, 6, false, true);
        check(state.update(b, gui, 320, 240, 5, 7, false, false) == 0, "New item starts at first page");
        state.update(b, gui, 320, 240, 5, 8, false, true);
        check(state.update(b, gui, 640, 360, 5, 9, false, false) == 0, "Resize resets");
        state.update(b, gui, 640, 360, 5, 10, false, true);
        check(state.update(b, gui, 640, 360, 5, 500, false, false) == 0, "Rehover resets");
        System.out.println("PASS layout: " + checks + " assertions; bounded panels, complete ordered content, colors, paging, 420 viewport/font cases");
    }
    private static void verify(int count, int w, int h, int fontHeight) {
        List<String> main = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            main.add("\u00a7b属性" + i + ": +208% 这里是一段较长的物品属性说明");
            if (i % 7 == 0) main.add("[M3LINE]");
        }
        main.add("\u00a7a[M3Min]小字测试 ABCD");
        List<List<String>> extra = Arrays.asList(Arrays.asList("\u00a76宝石名称", "\u00a7a宝石特殊效果 +30%"));
        List<String> saved = new ArrayList<String>(main);
        Layout layout = ColumnLayout.create(main, extra, FONT, w, h, 240, 6, 8, fontHeight);
        check(saved.equals(main), "Input tooltip never mutated");
        StringBuilder visible = new StringBuilder();
        int icons = 0;
        for (Panel panel : layout.panels) {
            check(panel.height + layout.footer <= h - 12, "Panel exceeds viewport: " + w + "x" + h + ": " + panel.height);
            for (Row row : panel.rows) {
                if (row.kind == ColumnLayout.ICON) icons++;
                if (row.kind != ColumnLayout.TEXT) continue;
                visible.append(plain(row.text));
                check(FONT.width(row.text) * (row.small ? .9 : 1) <= layout.panelWidth - 12 + .01, "Text exceeds panel width");
            }
        }
        StringBuilder expected = new StringBuilder();
        for (String s : main) if (!s.endsWith("[M3LINE]")) expected.append(plain(s.replace("[M3Min]", "")));
        for (List<String> group : extra) for (String s : group) expected.append(plain(s));
        check(expected.toString().equals(visible.toString()), "All attributes and extras retained in order");
        check(icons == (h - 12 >= 100 ? 1 : 0), "Icon rendered once");
        for (int page = 0; page < layout.pages; page++) for (int mx : new int[] {0, w / 2, w - 1}) {
            int x = layout.x(page, mx), y = layout.y(page, h - 1);
            check(x >= 6 && y >= 6, "Top/left screen margins");
            check(x + layout.groupWidth(page) <= w - 6, "Right screen margin");
            check(y + layout.groupHeight(page) <= h - 6, "Bottom screen margin");
        }
    }
    private static String plain(String s) { return s.replaceAll("\u00a7.", ""); }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
