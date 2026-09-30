package local.foxfoxm3tools;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import cpw.mods.fml.relauncher.Side;
import java.util.Map;

@IFMLLoadingPlugin.Name("FoxFoxM3Tools")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"local.foxfoxm3tools.tooltip.asm."})
public final class FoxFoxM3ToolsPlugin implements IFMLLoadingPlugin {
    @Override public String[] getASMTransformerClass() {
        return FMLLaunchHandler.side() == Side.CLIENT
                ? new String[] {"local.foxfoxm3tools.tooltip.asm.TooltipTransformer"} : new String[0];
    }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public String getAccessTransformerClass() { return null; }
    @Override public void injectData(Map<String, Object> data) { }
}
