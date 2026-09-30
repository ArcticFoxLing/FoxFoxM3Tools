package local.foxfoxm3tools.tooltip;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraftforge.common.config.Configuration;

public final class TooltipSettings {
    public static boolean enabled = true;
    public static int maxColumnWidth = 240;
    public static int screenMargin = 6;
    public static int columnGap = 8;

    private TooltipSettings() { }

    public static void load(File directory) {
        File current = new File(directory, "foxfoxm3tools.cfg");
        File legacy = new File(directory, "manametaltooltipcolumns.cfg");
        if (!current.exists() && legacy.isFile()) {
            try {
                Files.copy(legacy.toPath(), current.toPath());
            } catch (IOException error) {
                throw new IllegalStateException("FoxFoxM3Tools could not migrate tooltip configuration", error);
            }
        }
        Configuration config = new Configuration(current);
        enabled = config.getBoolean("enabled", "general", true, "Split overflowing ManaMetal item tooltips.");
        maxColumnWidth = config.getInt("maxColumnWidth", "general", 240, 100, 600,
                "Maximum width per column, in scaled GUI pixels. Long lines wrap with colors preserved.");
        screenMargin = config.getInt("screenMargin", "general", 6, 4, 24, "Space between tooltip and screen edges.");
        columnGap = config.getInt("columnGap", "general", 8, 4, 24, "Space between tooltip panels.");
        if (config.hasChanged()) config.save();
    }
}
