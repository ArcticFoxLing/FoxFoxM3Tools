package local.foxfoxm3tools.curse;

import java.lang.reflect.Field;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import project.studio.manametalmod.client.GuiCurse1;
import project.studio.manametalmod.client.GuiCurse2;
import project.studio.manametalmod.client.GuiScreenBase;

/** Reads the puzzle, then uses the original tile input and confirmation handlers. */
final class CurseAccess {
    private final GuiScreenBase gui;
    private final Field core, target, confirm;
    private final int symbols, tileY;

    static boolean supports(GuiScreen gui) {
        return gui != null && (gui.getClass() == GuiCurse1.class || gui.getClass() == GuiCurse2.class);
    }

    CurseAccess(GuiScreenBase gui) throws ReflectiveOperationException {
        this.gui = gui;
        boolean match = gui instanceof GuiCurse2;
        core = field("core");
        target = match ? field("core2") : null;
        confirm = field("Button1");
        symbols = match ? 6 : 4;
        tileY = match ? 86 : 8;
    }

    private Field field(String name) throws NoSuchFieldException {
        Field field = gui.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private int[] values(Field field) throws IllegalAccessException {
        int[] values = (int[])field.get(gui);
        if (values == null || values.length != 27) throw new IllegalStateException("Unexpected curse board size");
        for (int value : values)
            if (value < 0 || value >= symbols) throw new IllegalStateException("Unexpected curse symbol");
        return values;
    }

    /** Corrects at most one tile per tick; the following tick confirms a solved board. */
    boolean step() throws IllegalAccessException {
        int[] board = values(core);
        int[] wanted = target == null ? null : values(target);
        for (int i = 0; i < board.length; i++) {
            int goal = wanted == null ? 0 : wanted[i];
            int clicks = (goal - board[i] + symbols) % symbols;
            if (clicks == 0) continue;
            int x = 10 + (i % 9) * 24, y = tileY + (i / 9) * 24;
            for (int n = 0; n < clicks; n++) {
                boolean hit = gui instanceof GuiCurse1
                        ? ((GuiCurse1)gui).TestBox(gui.guiLeft + x + 9, gui.guiTop + y + 9, x, y, "", i)
                        : ((GuiCurse2)gui).TestBox(gui.guiLeft + x + 9, gui.guiTop + y + 9, x, y, "", i);
                if (!hit) throw new IllegalStateException("Original curse input rejected the tile");
            }
            if (board[i] != goal) throw new IllegalStateException("Original curse input did not reach target");
            return false;
        }
        boolean solved = gui instanceof GuiCurse1 ? ((GuiCurse1)gui).testthis() : ((GuiCurse2)gui).testthis();
        GuiButton button = (GuiButton)confirm.get(gui);
        if (!solved || button == null || !button.field_146124_l || !button.field_146125_m)
            throw new IllegalStateException("Original curse confirmation is unavailable");
        if (gui instanceof GuiCurse1) ((GuiCurse1)gui).func_146284_a(button);
        else ((GuiCurse2)gui).func_146284_a(button);
        return true;
    }
}
