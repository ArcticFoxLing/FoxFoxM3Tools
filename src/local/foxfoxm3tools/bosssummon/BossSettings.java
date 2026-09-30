package local.foxfoxm3tools.bosssummon;

import java.io.File;
import net.minecraftforge.common.config.Configuration;
import project.studio.manametalmod.bosssummon.BossType;

public final class BossSettings {
    private static Configuration config;
    private static int selected = -1;
    private BossSettings() { }

    public static void load(File directory) {
        config = new Configuration(new File(directory, "foxfoxm3tools-bosssummon.cfg"));
        config.load();
        String value = config.get("quickSummon", "boss", "",
                "Saved BossType name. Empty disables the held-ManaCrystal shortcut.").getString();
        selected = -1;
        try { selected = BossType.valueOf(value).ordinal(); }
        catch (IllegalArgumentException ignored) { }
        if (config.hasChanged()) config.save();
    }

    public static int selected() { return selected; }
    public static void save(int id) {
        selected = id >= 0 && id < BossType.values().length ? id : -1;
        config.get("quickSummon", "boss", "").set(selected < 0 ? "" : BossType.values()[selected].name());
        config.save();
    }
}
