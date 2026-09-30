package project.studio.manametalmod.produce.cuisine;

public final class MinigameCuisineAccess {
    private MinigameCuisineAccess() {}
    public static TileEntityCookingTable tile(GuiCuisine gui) { return gui.tile; }
    public static boolean finished(GuiCuisine gui) { return gui.maxGameTime <= 0; }
    public static void submit(GuiCuisine gui) {
        if (finished(gui) && gui.ok.field_146124_l) gui.func_146284_a(gui.ok);
    }
    public static boolean canSubmit(GuiCuisine gui) { return finished(gui) && gui.ok.field_146124_l; }

    /** Move only the bowl. Food, score, difficulty and the clock remain vanilla. */
    public static void steer(GuiCuisine gui) {
        if (finished(gui)) return;
        int best = gui.bowXpos;
        double bestScore = -Double.MAX_VALUE;
        for (int x = 4; x <= 152; x++) {
            double score = -Math.abs(x - gui.bowXpos) * 0.00001;
            for (FoodGame food : gui.foods) {
                if (food.y > 196 || food.x + 8 < x || food.x + 8 > x + 16) continue;
                if (food.y >= 170) score += food.isBad ? -100000 : 1000;
                else if (!food.isBad) {
                    int ticks = (170 - food.y + Math.max(1, food.moveSpeed) - 1) / Math.max(1, food.moveSpeed);
                    score += 1.0 / ticks;
                }
            }
            if (score > bestScore) { bestScore = score; best = x; }
        }
        gui.bowXpos = best;
    }
}
