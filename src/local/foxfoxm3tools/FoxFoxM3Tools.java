package local.foxfoxm3tools;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = "foxfoxm3tools", name = "狐狐魔金小工具", version = "1.7.0",
        acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "*",
        dependencies = "required-after:manametalmod")
public final class FoxFoxM3Tools {
    @SidedProxy(clientSide = "local.foxfoxm3tools.ClientProxy",
            serverSide = "local.foxfoxm3tools.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        proxy.init();
    }
}
