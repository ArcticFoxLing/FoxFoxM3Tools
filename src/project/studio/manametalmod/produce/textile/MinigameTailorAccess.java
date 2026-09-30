package project.studio.manametalmod.produce.textile;

public final class MinigameTailorAccess {
    private MinigameTailorAccess() {}
    public static TileEntityClothesTailor tile(GuiClothesTailor gui) { return gui.te; }
    public static void layout(GuiClothesTailor gui) { gui.Button1.field_146120_f = 71; }
}
