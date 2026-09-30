package project.studio.manametalmod.instance_dungeon;

import java.lang.reflect.Field;
import net.minecraft.client.gui.GuiButton;

/** Uses original start/key handlers, with an input sample for a skipped target. */
public final class LockpickAutoAccess {
    private static final Field START = field("start"), SUCCESS = field("success"), FAIL = field("fail"),
            HAS_ARROW = field("hasArrow"), ARROW = field("arrowPos"), YELLOW = field("yellowPos"),
            WIDTH = field("yellowWidth"), SPEED = field("arrowSpeed"), BUTTON = field("ButtonOK");

    private LockpickAutoAccess() { }

    private static Field field(String name) {
        try {
            Field result = GuiGameUnlock.class.getDeclaredField(name);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unsupported GuiGameUnlock field: " + name, error);
        }
    }

    public static boolean finished(GuiGameUnlock gui) throws IllegalAccessException {
        return SUCCESS.getBoolean(gui) || FAIL.getBoolean(gui);
    }

    public static boolean started(GuiGameUnlock gui) throws IllegalAccessException {
        return START.getBoolean(gui);
    }

    public static void start(GuiGameUnlock gui) throws IllegalAccessException {
        if (!started(gui) && !finished(gui)) gui.func_146284_a((GuiButton)BUTTON.get(gui));
    }

    public static final class Motion {
        private final float position, speed;
        private final int left, width;
        private Motion(GuiGameUnlock gui) throws IllegalAccessException {
            position = ARROW.getFloat(gui); speed = SPEED.getFloat(gui);
            left = YELLOW.getInt(gui); width = WIDTH.getInt(gui);
        }
    }

    /** Called at tick START, before Minecraft updates this screen. */
    public static Motion beforeTick(GuiGameUnlock gui) throws IllegalAccessException {
        return started(gui) && !finished(gui) && HAS_ARROW.getBoolean(gui) ? new Motion(gui) : null;
    }

    public static void play(GuiGameUnlock gui, Motion before) throws IllegalAccessException {
        if (!started(gui) || finished(gui) || !HAS_ARROW.getBoolean(gui)) return;
        // Match the game's integer centre-point test, including BOTH endpoints.
        // Press on the first legal tick: aiming for the middle can miss fast arrows.
        int centre = (int)ARROW.getFloat(gui) + 4;
        int left = YELLOW.getInt(gui), width = WIDTH.getInt(gui);
        if (width > 0 && centre >= left && centre <= left + width) {
            // Package access reaches the actual protected override, with no coremod.
            // It may clear the arrow OR move the target and slow the arrow down.
            gui.func_73869_a(' ', 57);
        } else if (before != null && width > 0 && before.speed > 0
                && before.left == left && before.width == width
                && before.speed == SPEED.getFloat(gui)
                && ARROW.getFloat(gui) == before.position + before.speed
                && (int)before.position + 4 < left && centre > left + width) {
            // At difficulty 1000 a tick can move 12.87 pixels across an 11-position
            // input window. Sample the actual swept path at its first legal point.
            // Never use an old sample after manual input, a reset or a target shift.
            float current = ARROW.getFloat(gui);
            float entry = left - 4;
            ARROW.setFloat(gui, entry);
            try {
                gui.func_73869_a(' ', 57);
            } finally {
                // clearArrow owns the reset. If the target moved instead, retain
                // the completed tick's movement and the ORIGINAL handler's slowdown.
                if (HAS_ARROW.getBoolean(gui) && ARROW.getFloat(gui) == entry)
                    ARROW.setFloat(gui, current);
            }
        }
    }
}
