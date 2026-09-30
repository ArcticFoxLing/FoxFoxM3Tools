package local.foxfoxm3tools.tooltip.layout;

import java.util.*;

/** Pure layout code; all measurements use scaled GUI coordinates, never physical pixels. */
public final class ColumnLayout {
    public static final int PAD = 6;
    public static final int HEADER = 12;
    public static final int FOOTER = 14;
    public static final int TEXT = 0, SEPARATOR = 1, ICON = 2;

    public interface Metrics { int width(String text); }

    public static final class Row {
        public final String text;
        public final int kind;
        public final int height;
        public final boolean small;
        public Row(String text, int kind, int height, boolean small) {
            this.text = text; this.kind = kind; this.height = height; this.small = small;
        }
    }

    public static final class Panel {
        public final List<Row> rows = new ArrayList<Row>();
        public int height = PAD * 2;
    }

    public static final class Layout {
        public final List<Panel> panels;
        public final int panelWidth, perPage, pages, margin, gap, screenWidth, screenHeight, footer;
        Layout(List<Panel> panels, int width, int perPage, int margin, int gap, int sw, int sh, int footer) {
            this.panels = Collections.unmodifiableList(panels); this.panelWidth = width;
            this.perPage = perPage; this.pages = (panels.size() + perPage - 1) / perPage;
            this.margin = margin; this.gap = gap; this.screenWidth = sw; this.screenHeight = sh;
            this.footer = footer;
        }
        public int visibleCount(int page) { return Math.min(perPage, panels.size() - page * perPage); }
        public int groupWidth(int page) { return visibleCount(page) * (panelWidth + gap) - gap; }
        public int groupHeight(int page) {
            int h = 0;
            for (int i = page * perPage; i < Math.min(panels.size(), (page + 1) * perPage); i++)
                h = Math.max(h, panels.get(i).height);
            return h + footer;
        }
        public int x(int page, int mouseX) {
            int width = groupWidth(page), x = mouseX + 12;
            if (x + width > screenWidth - margin) x = mouseX - 16 - width;
            return Math.max(margin, Math.min(x, screenWidth - margin - width));
        }
        public int y(int page, int mouseY) {
            return Math.max(margin, Math.min(mouseY + 4, screenHeight - margin - groupHeight(page)));
        }
    }

    public static Layout create(List<String> main, List<List<String>> extras, Metrics metrics,
            int sw, int sh, int maxWidth, int margin, int gap, int fontHeight) {
        int availableWidth = sw - margin * 2;
        int availableHeight = sh - margin * 2;
        if (availableWidth < 40 || availableHeight < 64) throw new IllegalArgumentException("GUI too small");
        int lineHeight = Math.max(10, fontHeight + 1);
        int width = Math.min(availableWidth, Math.min(maxWidth, naturalWidth(main, extras, metrics)));
        List<Row> rows = rows(main, extras, metrics, width - PAD * 2, lineHeight, availableHeight);
        int rawHeight = PAD * 2;
        for (Row row : rows) rawHeight += row.height;
        // A tall tooltip should get two readable panels when the viewport permits it.
        if (rawHeight > availableHeight && availableWidth >= 208) {
            width = Math.min(width, (availableWidth - gap) / 2);
            rows = rows(main, extras, metrics, width - PAD * 2, lineHeight, availableHeight);
        }
        int perPage = Math.max(1, (availableWidth + gap) / (width + gap));
        List<Panel> panels = split(rows, availableHeight);
        int footer = 0;
        if (panels.size() > perPage) {
            footer = FOOTER;
            panels = split(rows, availableHeight - footer);
        }
        return new Layout(panels, width, perPage, margin, gap, sw, sh, footer);
    }

    private static int naturalWidth(List<String> main, List<List<String>> extras, Metrics metrics) {
        int width = 100;
        for (String text : main) if (text != null && !text.endsWith("[M3LINE]"))
            width = Math.max(width, metrics.width(text.replace("[M3Min]", "")) + PAD * 2);
        for (List<String> group : extras) for (String text : group) if (text != null)
            width = Math.max(width, metrics.width(text.replace("[M3Min]", "")) + PAD * 2);
        return width;
    }

    private static List<Row> rows(List<String> main, List<List<String>> extras, Metrics metrics,
            int width, int lineHeight, int availableHeight) {
        List<Row> result = new ArrayList<Row>();
        for (int i = 0; i < main.size(); i++) {
            addText(result, main.get(i), width, metrics, lineHeight);
            if (i == 0 && availableHeight >= 100) result.add(new Row("", ICON, 42, false));
        }
        for (List<String> group : extras) {
            if (group.isEmpty()) continue;
            addText(result, "[M3LINE]", width, metrics, lineHeight);
            for (String text : group) addText(result, text, width, metrics, lineHeight);
        }
        return result;
    }

    private static void addText(List<Row> rows, String text, int width, Metrics metrics, int lineHeight) {
        if (text == null) return;
        if (text.endsWith("[M3LINE]")) {
            if (rows.isEmpty() || rows.get(rows.size() - 1).kind != SEPARATOR)
                rows.add(new Row("", SEPARATOR, 10, false));
            return;
        }
        boolean small = text.contains("[M3Min]");
        text = text.replace("[M3Min]", "");
        if (small) text = "\u00a7l" + text;
        int wrapWidth = small ? (int) Math.floor(width / 0.9) : width;
        for (String part : wrap(text, wrapWidth, metrics))
            rows.add(new Row(part, TEXT, small ? Math.max(9, (int) Math.ceil(lineHeight * .9)) : lineHeight, small));
    }

    /** Iterative wrapping also handles huge unbroken NBT descriptions without recursion. */
    public static List<String> wrap(String text, int maxWidth, Metrics metrics) {
        List<String> result = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        String formats = "";
        boolean hasGlyph = false;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i), count = Character.charCount(cp);
            if (cp == '\u00a7' && i + 1 < text.length()) {
                String code = text.substring(i, i + 2);
                char key = Character.toLowerCase(text.charAt(i + 1));
                if ("0123456789abcdefr".indexOf(key) >= 0) formats = key == 'r' ? "" : code;
                else if ("klmno".indexOf(key) >= 0 && formats.indexOf(code) < 0) formats += code;
                line.append(code); i += 2; continue;
            }
            if (cp == '\n') {
                result.add(line.toString()); line = new StringBuilder(formats); hasGlyph = false; i += count; continue;
            }
            String glyph = new String(Character.toChars(cp));
            if (hasGlyph && metrics.width(line.toString() + glyph) > maxWidth) {
                result.add(line.toString()); line = new StringBuilder(formats); hasGlyph = false;
            }
            line.append(glyph); hasGlyph = true; i += count;
        }
        result.add(line.toString());
        return result;
    }

    private static List<Panel> split(List<Row> rows, int maxHeight) {
        List<Panel> panels = new ArrayList<Panel>();
        Panel panel = new Panel(); panels.add(panel);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            // Keep a divider with the following attribute, instead of stranded at a column bottom.
            int need = row.height;
            if (row.kind == SEPARATOR && i + 1 < rows.size()) need += rows.get(i + 1).height;
            if (!panel.rows.isEmpty() && panel.height + need > maxHeight) {
                panel = new Panel(); panel.height += HEADER; panels.add(panel);
            }
            panel.rows.add(row); panel.height += row.height;
        }
        return panels;
    }
}
