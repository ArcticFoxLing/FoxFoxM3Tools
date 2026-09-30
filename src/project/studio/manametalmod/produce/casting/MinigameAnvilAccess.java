package project.studio.manametalmod.produce.casting;

import java.util.Random;
import net.minecraft.item.ItemStack;
import project.studio.manametalmod.network.MessageCastingTable;
import project.studio.manametalmod.network.PacketHandlerMana;

public final class MinigameAnvilAccess {
    private MinigameAnvilAccess() {}
    public static TileEntityCastingOther tile(GuiCastingAnvil gui) { return gui.te; }
    public static ItemStack item(GuiCastingAnvil gui) { return gui.field_147002_h.func_75139_a(0).func_75211_c(); }
    public static ItemStack hammer(GuiCastingAnvil gui) { return gui.field_147002_h.func_75139_a(1).func_75211_c(); }
    public static boolean workable(ItemStack item) {
        return item != null && item.func_77973_b() instanceof ItemCasting && item.func_77942_o()
                && item.func_77978_p().func_74762_e("id") == 0;
    }
    public static int value(ItemStack item, String key) { return item.func_77978_p().func_74762_e(key); }
    public static long identity(ItemStack item) {
        return ((long)ItemCasting.getSeed1(item) << 32) ^ (ItemCasting.getSeed2(item) & 0xffffffffL);
    }
    public static void prepare(GuiCastingAnvil gui, ItemStack item) {
        gui.item = item;
        Random random = new Random(ItemCasting.getSeed1(item));
        gui.type = 40 + random.nextInt(70);
        gui.need[0] = gui.type % 7;
        gui.need[1] = random.nextInt(8);
        random.setSeed(ItemCasting.getSeed2(item));
        gui.need[2] = random.nextInt(8);
    }
    public static int target(GuiCastingAnvil gui) { return gui.type; }
    public static int[] required(GuiCastingAnvil gui) { return gui.need.clone(); }
    public static void send(GuiCastingAnvil gui, int action) {
        PacketHandlerMana.INSTANCE.sendToServer(new MessageCastingTable(action,
                gui.te.field_145851_c, gui.te.field_145848_d, gui.te.field_145849_e, true, 1));
    }
    public static boolean acknowledge(GuiCastingAnvil gui, int action) {
        // Record the local history only after the server has applied the hit.
        // addAnvil rechecks temperature, which can cool between send and reply;
        // the acknowledged hit must still be recorded in that case.
        gui.anvilUse.add(0, action);
        return gui.isDone(value(item(gui), "forge"));
    }
}
