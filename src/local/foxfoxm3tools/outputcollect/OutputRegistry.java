package local.foxfoxm3tools.outputcollect;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import project.studio.manametalmod.produce.ProduceCore;
import project.studio.manametalmod.produce.brewing.TileEntityAdvancedBrewing;
import project.studio.manametalmod.originmagic.TileEntityEternalAnvil;

/** Audited ManaMetal 8.0.7 output INVENTORY indices, not container slot numbers.
 * Never infer outputs from rejecting insertion: recipe previews can do that too.
 */
public final class OutputRegistry {
    private static final Map<String, int[]> OUTPUTS = new LinkedHashMap<String, int[]>();
    static {
        add("project.studio.manametalmod.inventory.ContainerGemCraft", 0);
        add("project.studio.manametalmod.inventory.ContainerBedrcokOre", range(20, 40));
        add("project.studio.manametalmod.inventory.ContainerAlloyFurnace", 2, 3);
        add("project.studio.manametalmod.inventory.ContainerBedrockCrusher", 2);
        add("project.studio.manametalmod.inventory.ContainerCheeseMaker", 2);
        add("project.studio.manametalmod.inventory.ContainerDiamondCompressor", 2, 3);
        add("project.studio.manametalmod.inventory.ContainerEffectBaublesMaker", 2, 3);
        add("project.studio.manametalmod.inventory.ContainerEXPExtractor", 2);
        add("project.studio.manametalmod.inventory.ContainerIronCrusher", 2);
        add("project.studio.manametalmod.inventory.ContainerIronPlateMaker", 2, 3);
        add("project.studio.manametalmod.inventory.ContainerIronWroughtContainer", 2, 3);
        add("project.studio.manametalmod.inventory.ContainerIronWroughtFurnace", 2);
        add("project.studio.manametalmod.inventory.ContainerIronWroughtSteelF", 2);
        add("project.studio.manametalmod.inventory.ContainerManaFuelMake", 2, 3);
        add("project.studio.manametalmod.inventory.ContainerManaFurnace", 2);
        add("project.studio.manametalmod.inventory.ContainerManaMake1", 2, 3);
        add("project.studio.manametalmod.inventory.ContainerManaSF", 2);
        add("project.studio.manametalmod.inventory.ContainerManaSFurnace", 2, 3);
        add("project.studio.manametalmod.inventory.ContainerMetalFurnace", 2);
        add("project.studio.manametalmod.inventory.ContainerMetalSeparator", 2, 3);
        add("project.studio.manametalmod.inventory.ContainerTimeFurnace", 2);
        add("project.studio.manametalmod.inventory.ContainerManaGravityWell", 12);
        add("project.studio.manametalmod.dark_magic.ContainerTileEntityDarkMain", 0);
        add("project.studio.manametalmod.Lapuda.ContainerTileEntityBlueSky", 0);
        add("project.studio.manametalmod.world.thuliumempire.ContainerAncientEmpireCore", 0);
        add("project.studio.manametalmod.originmagic.ContainerOriginCrystalCore", 0);
        add("project.studio.manametalmod.inventory.ContainerManaCraftTable", 9);
        add("project.studio.manametalmod.inventory.ContainerMetalCraftTable", 9);
        add("project.studio.manametalmod.inventory.ContainerCastingTable", 22);
        add("project.studio.manametalmod.inventory.ContainerCookingTable", 7);
        add("project.studio.manametalmod.inventory.ContainerMagicPot", 5);
        add("project.studio.manametalmod.inventory.ContainerCuttingBoard", 1);
        add("project.studio.manametalmod.inventory.ContainerSpinningWheel", 5);
        add("project.studio.manametalmod.inventory.ContainerBedrockMaker", 2);
        add("project.studio.manametalmod.inventory.ContainerTileEntityBase", 1);
        add("project.studio.manametalmod.inventory.ContainerPrayerAltar", 0);
        add("project.studio.manametalmod.dark_magic.ContainerTileEntityDarkSteelBlast", 1);
        add("project.studio.manametalmod.dark_magic.ContainerTileEntityDarkSteelFurnace", 1);
        add("project.studio.manametalmod.dark_magic.ContainerTileEntityDarkItemMake", range(1, 37));
        add("project.studio.manametalmod.dark_magic.ContainerTileEntityDarkFission", range(1, 37));
        add("project.studio.manametalmod.inventory.ContainerOrePurification", 1);
        add("project.studio.manametalmod.inventory.ContainerMetalReduction", range(19, 28));
        add("project.studio.manametalmod.inventory.ContainerSieve", range(1, 16));
        add("project.studio.manametalmod.produce.textile.ContainerClothesTailor", 5);
        add("project.studio.manametalmod.originmagic.ContainerPrimalForge", range(45, 54));
        add("project.studio.manametalmod.originmagic.ContainerMirrorPool", range(1, 46));
        add("project.studio.manametalmod.produce.brewing.ContainerAdvancedBrewing", 5, 6, 7);
        add("project.studio.manametalmod.produce.beekeeping.ContainerBeeBreeding", 2);
        add("project.studio.manametalmod.produce.beekeeping.ContainerBeecultivate", range(18, 36));
        add("project.studio.manametalmod.originmagic.ContainerEternalAnvil", 27);
        add("project.studio.manametalmod.inventory.ContainerRecycling", range(0, 54));
    }
    private OutputRegistry() { }
    private static void add(String name, int... slots) { OUTPUTS.put(name, slots); }
    private static int[] range(int first, int end) {
        int[] result = new int[end - first];
        for (int i = 0; i < result.length; i++) result[i] = first + i;
        return result;
    }
    public static boolean supports(Container container) {
        return container != null && OUTPUTS.containsKey(container.getClass().getName());
    }
    public static int[] slots(Container container, EntityPlayer player) {
        if (!supports(container) || player == null) return null;
        int[] inventorySlots = OUTPUTS.get(container.getClass().getName());
        int[] result = new int[inventorySlots.length];
        IInventory machine = null;
        int storageMaskCount = 0;
        boolean[] seen = new boolean[36];
        for (Object entry : container.field_75151_b) {
            Slot slot = (Slot)entry;
            if (CollectActions.isPlayerStorage(slot, player) && !seen[slot.getSlotIndex()]) {
                seen[slot.getSlotIndex()] = true; storageMaskCount++;
            }
        }
        if (storageMaskCount != 36) return null;
        for (int i = 0; i < inventorySlots.length; i++) {
            int found = -1;
            for (int s = 0; s < container.field_75151_b.size(); s++) {
                Slot slot = container.func_75139_a(s);
                if (!(slot.field_75224_c instanceof TileEntity) || slot.getSlotIndex() != inventorySlots[i]) continue;
                if (found >= 0 || (machine != null && machine != slot.field_75224_c)) return null;
                found = s; machine = slot.field_75224_c;
            }
            if (found < 0) return null;
            result[i] = found;
        }
        return result;
    }
    public static boolean finished(Slot slot, ItemStack item) {
        if (slot.field_75224_c instanceof TileEntityAdvancedBrewing) {
            // Bottles and finished potions share three slots. Never remove input bottles.
            TileEntityAdvancedBrewing tile = (TileEntityAdvancedBrewing)slot.field_75224_c;
            return tile.Star != 1 && item.func_77973_b() == ProduceCore.ItemAdvancedPotionE;
        }
        if (slot.field_75224_c instanceof TileEntityEternalAnvil) {
            // The output is also the reforge input: only collect the selected crafting result.
            TileEntityEternalAnvil tile = (TileEntityEternalAnvil)slot.field_75224_c;
            return !tile.isWorking() && tile.getSelectedRecipe() != null
                    && CollectActions.sameKind(item, tile.getSelectedRecipe().getResult());
        }
        return true;
    }
}
