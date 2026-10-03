package local.foxfoxm3tools.outputcollect;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Normal inventory clicks only; never drop, quick-craft, or modify inventories directly. */
public final class CollectActions {
    public interface Clicker { void click(int slot); }
    public enum Result { EMPTY, FULL, MOVED, PAUSED, STOPPED }
    private CollectActions() { }

    public static Result collect(Container container, EntityPlayer player, int[] outputs, Clicker clicker) {
        if (player.field_71071_by.func_70445_o() != null) return Result.PAUSED;
        boolean full = false;
        for (int id : outputs) {
            Slot source = container.func_75139_a(id);
            ItemStack stack = source.func_75211_c();
            if (stack == null || stack.field_77994_a <= 0 || !source.func_82869_a(player)
                    || !OutputRegistry.finished(source, stack)) continue;
            List<Integer> plan = destinations(container, player, stack);
            if (plan == null) { full = true; continue; }
            // Reserve enough space for the ENTIRE stack before picking it up. Output slots
            // often reject insertion, so picking up a partial-fit stack would strand a cursor.
            ItemStack expected = stack.func_77946_l();
            clicker.click(id);
            ItemStack held = player.field_71071_by.func_70445_o();
            if (!ItemStack.func_77989_b(expected, held)) return Result.STOPPED;
            for (int destination : plan) {
                held = player.field_71071_by.func_70445_o();
                if (held == null) return Result.MOVED;
                Slot target = container.func_75139_a(destination);
                // Recheck after each click: another handler must not make us swap a user's item.
                if (!sameKind(expected, held) || room(target, held) <= 0) return Result.STOPPED;
                clicker.click(destination);
            }
            return player.field_71071_by.func_70445_o() == null ? Result.MOVED : Result.STOPPED;
        }
        return full ? Result.FULL : Result.EMPTY;
    }

    private static List<Integer> destinations(Container container, EntityPlayer player, ItemStack stack) {
        List<Integer> result = new ArrayList<Integer>();
        int remaining = stack.field_77994_a;
        // Merge matching NBT first, then fill empty slots. Only the normal 36 player slots.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < container.field_75151_b.size(); i++) {
                Slot slot = container.func_75139_a(i);
                if (!isPlayerStorage(slot, player) || (slot.func_75211_c() == null) != (pass == 1)) continue;
                int space = room(slot, stack);
                if (space <= 0) continue;
                result.add(i);
                remaining -= space;
                if (remaining <= 0) return result;
            }
        }
        return null;
    }

    public static boolean isPlayerStorage(Slot slot, EntityPlayer player) {
        return slot.field_75224_c == player.field_71071_by && slot.getSlotIndex() >= 0
                && slot.getSlotIndex() < 36;
    }

    private static int room(Slot slot, ItemStack item) {
        if (!slot.func_75214_a(item)) return 0;
        ItemStack present = slot.func_75211_c();
        int max = Math.min(slot.func_75219_a(), item.func_77976_d());
        if (present == null) return max;
        if (!item.func_77985_e() || !sameKind(item, present)) return 0;
        return Math.max(0, Math.min(max, present.func_77976_d()) - present.field_77994_a);
    }

    static boolean sameKind(ItemStack a, ItemStack b) {
        return a != null && b != null && a.func_77973_b() == b.func_77973_b()
                && a.func_77960_j() == b.func_77960_j() && ItemStack.func_77970_a(a, b);
    }
}
