package local.foxfoxm3tools;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/** Keeps client classes out of dedicated-server class loading. */
public class CommonProxy {
    public void preInit(FMLPreInitializationEvent event) { }
    public void init() { }
}
