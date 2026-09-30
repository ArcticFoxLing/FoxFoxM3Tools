package local.foxfoxm3tools.tooltip.layout;

/** Edge-triggered paging; holding a key cannot spin through pages every frame. */
public final class PageState {
    private Object item, screen;
    private int width, height, page;
    private long lastSeen;
    private boolean previousUp, previousDown;
    public int update(Object item, Object screen, int width, int height, int pages,
            long now, boolean up, boolean down) {
        boolean reset = item != this.item || screen != this.screen || width != this.width
                || height != this.height || now - lastSeen > 300;
        if (reset) { page = 0; previousUp = up; previousDown = down; }
        else if (down && !previousDown) page = Math.min(pages - 1, page + 1);
        else if (up && !previousUp) page = Math.max(0, page - 1);
        this.item = item; this.screen = screen; this.width = width; this.height = height;
        lastSeen = now; previousUp = up; previousDown = down;
        page = Math.max(0, Math.min(page, pages - 1));
        return page;
    }
}
