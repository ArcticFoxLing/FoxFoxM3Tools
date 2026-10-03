package local.foxfoxvalidation;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.NetworkModHolder;
import cpw.mods.fml.relauncher.Side;
import java.io.File;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;

/** Integration checks for combining separate Forge entries into one addon. */
public final class MergedIdentity {
    public static net.minecraft.network.NetworkManager offlineNetwork() {
        return new net.minecraft.network.NetworkManager(true) {
            @Override public java.net.SocketAddress func_74430_c() {
                return new java.net.InetSocketAddress("127.0.0.1", 0);
            }
            @Override public void func_150725_a(net.minecraft.network.Packet packet,
                    io.netty.util.concurrent.GenericFutureListener... listeners) { }
        };
    }
    public static void verify() throws Exception {
        for (net.minecraft.client.audio.SoundCategory category : net.minecraft.client.audio.SoundCategory.values())
            require(Minecraft.func_71410_x().field_71474_y.func_151438_a(category) == 0.0F,
                    "isolated test client is muted: " + category);
        System.out.println("PASS isolated test audio: master and every sound category are muted");
        Map<String, ModContainer> loaded = Loader.instance().getIndexedModList();
        for (String id : new String[] {"manametalminigameauto", "manametalrewardautoclose",
                "manametalspiritualautoreset", "manametaltooltipcolumns", "manametallockpickauto",
                "manametalbeehiveclient", "manametalbeehiveauto", "manametalcurseauto", "manametalchessauto", "manametalwaterauto"})
            require(!loaded.containsKey(id), "old standalone entry absent: " + id);
        for (String id : System.getProperty("foxfox.test.mods").split(",")) {
            ModContainer mod = loaded.get(id);
            String name = "foxfoxfix".equals(id) ? "狐狐修复" : "狐狐魔金小工具";
            require(mod != null, "merged mod loaded: " + id);
            require(name.equals(mod.getName()), "Forge Chinese display name: " + id);
            require(name.equals(mod.getMetadata().name), "mod list Chinese name: " + id);
            if ("foxfoxm3tools".equals(id))
                require("1.10.0".equals(mod.getVersion()) && "1.10.0".equals(mod.getMetadata().version),
                        "merged release version");
            int count = 0;
            for (ModContainer other : loaded.values())
                if (mod.getSource().equals(other.getSource())) count++;
            require(count == 1, "exactly one visible mod from release JAR");
            NetworkModHolder holder = NetworkRegistry.INSTANCE.registry().get(mod);
            require(holder != null && holder.check(Collections.<String,String>emptyMap(), Side.SERVER),
                    "remote server need not install " + id);
        }
        if (loaded.containsKey("foxfoxfix")) {
            transformer("local.foxfoxfix.modularui.TooltipTransformer");
            transformer("local.foxfoxfix.scythe.ScytheTransformer");
        }
        if (loaded.containsKey("foxfoxm3tools")) {
            transformer("local.foxfoxm3tools.tooltip.asm.TooltipTransformer");
            for (String name : new String[] {"local.foxfoxm3tools.minigame.ClientHooks",
                    "local.foxfoxm3tools.archeology.ClientHooks", "local.foxfoxm3tools.lockpick.ClientHooks",
                    "local.foxfoxm3tools.reward.HolyDeviceGuiHooks",
                    "local.foxfoxm3tools.dungeon.DungeonResetGuiHooks",
                    "local.foxfoxm3tools.beehive.ClientHooks",
                    "local.foxfoxm3tools.bosssummon.ClientHooks",
                    "local.foxfoxm3tools.spiritual.ClientHooks",
                    "local.foxfoxm3tools.outputcollect.ClientHooks",
                    "local.foxfoxm3tools.curse.ClientHooks",
                    "local.foxfoxm3tools.chess.ClientHooks",
                    "local.foxfoxm3tools.water.ClientHooks"}) {
                listener(MinecraftForge.EVENT_BUS, name);
                listener(FMLCommonHandler.instance().bus(), name);
            }
            listener(FMLCommonHandler.instance().bus(), "local.foxfoxm3tools.reward.ClientGuiHooks");
            require("自动：关".equals(StatCollector.func_74838_a("minigameauto.off")), "merged Chinese language resource");
            require("自动：关".equals(StatCollector.func_74838_a("lockpickauto.off")), "merged lockpick Chinese resource");
            require("丢出产物".equals(StatCollector.func_74838_a("beehiveclient.drop"))
                    && "补充蜜蜂".equals(StatCollector.func_74838_a("beehiveclient.refill")), "merged beehive Chinese resources");
            require("设为快捷召唤".equals(StatCollector.func_74838_a("bosssummonquick.save")), "boss shortcut Chinese resource");
            listener(FMLCommonHandler.instance().bus(), "local.foxfoxm3tools.skill.ClientHooks");
            require("自动：关".equals(StatCollector.func_74838_a("curseauto.off")), "merged curse language resource");
            int chessKeys = 0;
            for (net.minecraft.client.settings.KeyBinding key : Minecraft.func_71410_x().field_71474_y.field_74324_K) {
                if (!"key.mmchess.toggle".equals(key.func_151464_g())) continue;
                chessKeys++;
                require("key.categories.foxfoxm3tools".equals(key.func_151466_e()), "chess control in merged category");
            }
            require(chessKeys == 1, "one chess binding, legacy settings key preserved");
            int waterKeys = 0;
            for (net.minecraft.client.settings.KeyBinding key : Minecraft.func_71410_x().field_71474_y.field_74324_K) {
                if (!"key.mmwater.toggle".equals(key.func_151464_g())) continue;
                waterKeys++;
                require("key.categories.foxfoxm3tools".equals(key.func_151466_e()), "water control in merged category");
                require(!key.func_151464_g().equals(StatCollector.func_74838_a(key.func_151464_g())), "water control translated");
            }
            require(waterKeys == 1, "one water binding, legacy settings key preserved");
            migrateConfig();
        }
        System.out.println("PASS merged integration: Chinese mod names, one entry per JAR, unique hooks, optional server installation, resources/config");
    }
    private static void transformer(String name) {
        int count = 0;
        for (IClassTransformer transformer : Launch.classLoader.getTransformers())
            if (name.equals(transformer.getClass().getName())) count++;
        require(count == 1, "one active transformer: " + name);
    }
    private static void listener(EventBus bus, String name) throws Exception {
        Field field = EventBus.class.getDeclaredField("listeners");
        field.setAccessible(true);
        int count = 0;
        for (Object instance : ((Map<?,?>)field.get(bus)).keySet())
            if (name.equals(instance.getClass().getName())) count++;
        require(count == 1, "one listener instance: " + name);
    }
    private static void migrateConfig() throws Exception {
        Class<?> settings = Class.forName("local.foxfoxm3tools.tooltip.TooltipSettings");
        Method load = settings.getMethod("load", File.class);
        File game = Minecraft.func_71410_x().field_71412_D;
        Path temp = Files.createTempDirectory(game.toPath().getParent(), "config-migration-");
        byte[] legacy = ("general {\n B:enabled=false\n I:maxColumnWidth=333\n I:screenMargin=9\n I:columnGap=11\n}\n").getBytes(StandardCharsets.UTF_8);
        Path old = temp.resolve("manametaltooltipcolumns.cfg"), current = temp.resolve("foxfoxm3tools.cfg");
        Files.write(old, legacy);
        try {
            load.invoke(null, temp.toFile());
            require(Files.isRegularFile(current), "new config created from legacy");
            require(!settings.getField("enabled").getBoolean(null)
                    && settings.getField("maxColumnWidth").getInt(null) == 333
                    && settings.getField("screenMargin").getInt(null) == 9
                    && settings.getField("columnGap").getInt(null) == 11, "legacy settings retained");
            Files.write(current, "general {\n B:enabled=true\n I:maxColumnWidth=222\n I:screenMargin=7\n I:columnGap=9\n}\n".getBytes(StandardCharsets.UTF_8));
            load.invoke(null, temp.toFile());
            require(settings.getField("enabled").getBoolean(null)
                    && settings.getField("maxColumnWidth").getInt(null) == 222, "existing new config wins");
            require(Arrays.equals(legacy, Files.readAllBytes(old)), "legacy config left intact");
        } finally {
            load.invoke(null, new File(game, "config"));
        }
    }
    private static void require(boolean ok, String description) {
        if (!ok) throw new AssertionError(description);
    }
}
