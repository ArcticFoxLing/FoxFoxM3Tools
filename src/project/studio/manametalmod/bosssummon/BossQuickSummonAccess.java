package project.studio.manametalmod.bosssummon;

/** Package access to the original screen, without replacing it or its rules. */
public final class BossQuickSummonAccess {
    private BossQuickSummonAccess() { }
    public static TileEntityBossSpawn tile(GuiTileEntityBossSummon gui) { return gui.te; }
    public static int selected(GuiTileEntityBossSummon gui) { return gui.setID; }
    public static void summon(GuiTileEntityBossSummon gui, int id) {
        if (id < 0 || id >= BossType.values().length) return;
        gui.setID = id;
        gui.Button1.field_146124_l = true;
        gui.func_146284_a(gui.Button1);
    }
}
