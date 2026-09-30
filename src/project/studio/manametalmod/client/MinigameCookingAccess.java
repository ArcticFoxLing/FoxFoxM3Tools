package project.studio.manametalmod.client;

import project.studio.manametalmod.produce.cuisine.TileEntityCookingTable;

public final class MinigameCookingAccess {
    private MinigameCookingAccess() {}
    public static TileEntityCookingTable tile(GuiCookingTable gui) { return gui.te; }
}
