package local.foxfoxm3tools.beehive;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import project.studio.manametalmod.produce.beekeeping.BeekeepingCore;
import project.studio.manametalmod.produce.beekeeping.ContainerHoneycomb;

/** All mutations are performed by vanilla windowClick, including client prediction. */
public final class HiveActions {
    public interface Clicker { void click(int slot, int button, int mode); }
    private HiveActions() {}

    public static boolean hasProducts(ContainerHoneycomb container, EntityPlayer player) {
        for (int slot = 3; slot < 27; slot++) {
            Slot output = container.func_75139_a(slot);
            if (output.func_75216_d() && output.func_82869_a(player)) return true;
        }
        return false;
    }

    public static boolean canRefill(ContainerHoneycomb container, EntityPlayer player) {
        for (int target = 1; target <= 2; target++) {
            for (Slot source : supplies(container, player)) {
                if (room(container.func_75139_a(target), source.func_75211_c()) > 0) return true;
            }
        }
        return false;
    }

    public static void dropProducts(ContainerHoneycomb container, EntityPlayer player, Clicker clicks) {
        if (player.field_71071_by.func_70445_o() != null) return;
        for (int slot = 3; slot < 27; slot++) {
            Slot output = container.func_75139_a(slot);
            if (output.func_75216_d() && output.func_82869_a(player))
                clicks.click(slot, 1, 4); // Ctrl+Q: drop this entire output stack.
        }
    }

    public static void refill(ContainerHoneycomb container, EntityPlayer player, Clicker clicks) {
        if (player.field_71071_by.func_70445_o() != null) return;
        List<Slot> sources = supplies(container, player);
        // Top up occupied worker slots before populating empty ones. Never target slot 0.
        for (int pass = 0; pass < 2; pass++) {
            for (int index = 1; index <= 2; index++) {
                Slot target = container.func_75139_a(index);
                if ((pass == 0) != target.func_75216_d()) continue;
                for (Slot source : sources) {
                    ItemStack supply = source.func_75211_c();
                    if (!worker(supply) || room(target, supply) <= 0) continue;
                    clicks.click(source.field_75222_d, 0, 0); // Pick up supply.
                    if (!worker(player.field_71071_by.func_70445_o())) return;
                    clicks.click(target.field_75222_d, 0, 0); // Fill up to the real item/slot cap.
                    if (player.field_71071_by.func_70445_o() != null)
                        clicks.click(source.field_75222_d, 0, 0); // Return excess to its original slot.
                    if (player.field_71071_by.func_70445_o() != null) return;
                }
            }
        }
    }

    private static List<Slot> supplies(ContainerHoneycomb container, EntityPlayer player) {
        List<Slot> result = new ArrayList<Slot>();
        for (Object object : container.field_75151_b) {
            Slot slot = (Slot)object;
            if (slot.field_75224_c == player.field_71071_by && slot.getSlotIndex() >= 0
                    && slot.getSlotIndex() < 36 && slot.func_82869_a(player)
                    && worker(slot.func_75211_c())) result.add(slot);
        }
        return result;
    }

    private static boolean worker(ItemStack stack) {
        return stack != null && stack.field_77994_a > 0 && stack.func_77973_b() == BeekeepingCore.beebase;
    }

    private static int room(Slot target, ItemStack supply) {
        if (!worker(supply) || !target.func_75214_a(supply)) return 0;
        ItemStack current = target.func_75211_c();
        if (current != null && (!current.func_77969_a(supply) || !ItemStack.func_77970_a(current, supply))) return 0;
        return Math.min(target.func_75219_a(), supply.func_77976_d()) - (current == null ? 0 : current.field_77994_a);
    }
}
