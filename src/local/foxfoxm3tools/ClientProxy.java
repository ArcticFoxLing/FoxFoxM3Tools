package local.foxfoxm3tools;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import local.foxfoxm3tools.reward.ClientGuiHooks;
import local.foxfoxm3tools.tooltip.TooltipSettings;
import net.minecraftforge.common.MinecraftForge;

@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {
    private boolean initialized;

    @Override public void preInit(FMLPreInitializationEvent event) {
        TooltipSettings.load(event.getModConfigurationDirectory());
        local.foxfoxm3tools.bosssummon.BossSettings.load(event.getModConfigurationDirectory());
    }

    @Override public void init() {
        if (initialized) return;
        initialized = true;
        registerGuiHooks(new local.foxfoxm3tools.minigame.ClientHooks());
        registerGuiHooks(new local.foxfoxm3tools.archeology.ClientHooks());
        registerGuiHooks(new local.foxfoxm3tools.lockpick.ClientHooks());
        registerGuiHooks(new local.foxfoxm3tools.beehive.ClientHooks());
        registerGuiHooks(new local.foxfoxm3tools.bosssummon.ClientHooks());
        registerGuiHooks(new local.foxfoxm3tools.spiritual.ClientHooks());
        registerGuiHooks(new local.foxfoxm3tools.outputcollect.ClientHooks());
        registerGuiHooks(new local.foxfoxm3tools.curse.ClientHooks());
        local.foxfoxm3tools.chess.ClientHooks chess = new local.foxfoxm3tools.chess.ClientHooks();
        registerGuiHooks(chess);
        cpw.mods.fml.client.registry.ClientRegistry.registerKeyBinding(chess.toggle);
        FMLCommonHandler.instance().bus().register(new ClientGuiHooks());
        registerGuiHooks(new local.foxfoxm3tools.reward.HolyDeviceGuiHooks());
        registerGuiHooks(new local.foxfoxm3tools.dungeon.DungeonResetGuiHooks());
        FMLCommonHandler.instance().bus().register(new local.foxfoxm3tools.skill.ClientHooks());
    }

    private static void registerGuiHooks(Object hooks) {
        MinecraftForge.EVENT_BUS.register(hooks);
        FMLCommonHandler.instance().bus().register(hooks);
    }
}
