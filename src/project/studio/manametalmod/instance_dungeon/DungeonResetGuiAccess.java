package project.studio.manametalmod.instance_dungeon;

/** Match the original client-only loading screen to the clicked entrance. */
public final class DungeonResetGuiAccess {
    private DungeonResetGuiAccess() { }
    public static TileEntityInstanceDungeon tile(GuiInstanceDungeonLoad gui) { return gui.tile; }
}
