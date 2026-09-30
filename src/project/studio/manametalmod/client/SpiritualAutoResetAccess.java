package project.studio.manametalmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import project.studio.manametalmod.MMM;
import project.studio.manametalmod.core.DayResource;
import project.studio.manametalmod.core.SpiritualPower;
import project.studio.manametalmod.entity.nbt.ManaMetalModRoot;
import project.studio.manametalmod.itemAndBlockCraft.ItemCraft2;

/** Narrow package-access bridge; no original classes are replaced or transformed. */
public final class SpiritualAutoResetAccess {
    private SpiritualAutoResetAccess() { }

    public static ManaMetalModRoot root() {
        return MMM.getEntityNBT(Minecraft.func_71410_x().field_71439_g);
    }

    public static int stones() {
        return MMM.testPlayerItemsCountNoNBT(Minecraft.func_71410_x().field_71439_g,
                MMM.item(ItemCraft2.ItemUltimateSoulGem));
    }

    public static int day(ManaMetalModRoot root) {
        return root.carrer.getDayResource(DayResource.SpiritualPower);
    }

    public static boolean busy(GuiSpiritualPower gui) { return gui.doit != -1; }
    public static boolean locked(GuiSpiritualPower gui, int stage) { return gui.lock[stage]; }
    public static GuiButton resetButton(GuiSpiritualPower gui, int stage) { return gui.set[stage]; }

    public static boolean reset(GuiSpiritualPower gui, int stage) {
        if (busy(gui) || locked(gui, stage) || !gui.set[stage].field_146124_l) return false;
        // Uses the original request, animation, local item counters, and lock array.
        gui.func_146284_a(gui.set[stage]);
        return gui.doit == stage;
    }

    public static void refresh(GuiSpiritualPower gui, ManaMetalModRoot root) {
        if (busy(gui)) return;
        gui.root = root;
        gui.data = root.carrer.SpiritualPower;
        gui.dataFloat = root.carrer.SpiritualPowerData;
        gui.SpiritualPowerCount = root.carrer.SpiritualPowerCount;
        gui.LV = root.carrer.getLv();
        gui.size = 0;
        while (gui.size < 8 && gui.LV >= SpiritualPower.getNeedLV(gui.size)) gui.size++;
        EntityPlayer player = Minecraft.func_71410_x().field_71439_g;
        gui.ItemUltimateSoulGem = stones();
        gui.ItemClover = MMM.testPlayerItemsCountNoNBT(player, MMM.item(ItemCraft2.ItemClover));
        gui.ItemGoldenBeetle = MMM.testPlayerItemsCountNoNBT(player, MMM.item(ItemCraft2.ItemGoldenBeetle));
    }
}
