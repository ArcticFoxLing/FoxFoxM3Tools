package local.outputcollecttest;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.init.Items;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import project.studio.manametalmod.core.FuelType;
import project.studio.manametalmod.tileentity.TileEntityBase;
import project.studio.manametalmod.produce.textile.TileEntitySpinningWheel;
import project.studio.manametalmod.produce.brewing.TileEntityAdvancedBrewing;
import project.studio.manametalmod.produce.ProduceCore;
import project.studio.manametalmod.originmagic.TileEntityEternalAnvil;

/** Independent fixture catalogue, usable on a server WITHOUT the addon. */
public final class OutputFixtures {
    public static final class Entry {
        public final String container, tile, gui;
        public final int[] outputs;
        Entry(String container, String tile, String gui, int[] outputs) {
            this.container=container; this.tile=tile; this.gui=gui; this.outputs=outputs;
        }
    }
    public static final Entry[] ENTRIES = {
        new Entry("project.studio.manametalmod.inventory.ContainerGemCraft", "project.studio.manametalmod.produce.gemcraft.TileEntityGemCraft", "project.studio.manametalmod.client.GuiGemCraft", new int[]{0}),
        new Entry("project.studio.manametalmod.inventory.ContainerBedrcokOre", "project.studio.manametalmod.tileentity.TileEntityBedrockOre", "project.studio.manametalmod.client.GuiBedrockOre", new int[]{20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39}),
        new Entry("project.studio.manametalmod.inventory.ContainerAlloyFurnace", "project.studio.manametalmod.tileentity.TileEntityAlloyFurnace", "project.studio.manametalmod.client.GuiAlloyFurnace", new int[]{2,3}),
        new Entry("project.studio.manametalmod.inventory.ContainerBedrockCrusher", "project.studio.manametalmod.tileentity.TileEntityBedrockCrusher", "project.studio.manametalmod.client.GuiBedrockCrusher", new int[]{2}),
        new Entry("project.studio.manametalmod.inventory.ContainerCheeseMaker", "project.studio.manametalmod.tileentity.TileEntityCheeseMaker", "project.studio.manametalmod.client.GuiCheeseMaker", new int[]{2}),
        new Entry("project.studio.manametalmod.inventory.ContainerDiamondCompressor", "project.studio.manametalmod.tileentity.TileEntityDiamondCompressor", "project.studio.manametalmod.client.GuiDiamondCompressor", new int[]{2,3}),
        new Entry("project.studio.manametalmod.inventory.ContainerEffectBaublesMaker", "project.studio.manametalmod.tileentity.TileEntityEffectBaublesMaker", "project.studio.manametalmod.client.GuiEffectBaublesMaker", new int[]{2,3}),
        new Entry("project.studio.manametalmod.inventory.ContainerEXPExtractor", "project.studio.manametalmod.tileentity.TileEntityEXPExtractor", "project.studio.manametalmod.client.GuiEXPExtractor", new int[]{2}),
        new Entry("project.studio.manametalmod.inventory.ContainerIronCrusher", "project.studio.manametalmod.tileentity.TileEntityCrusherMetal", "project.studio.manametalmod.client.GuiIronCrusher", new int[]{2}),
        new Entry("project.studio.manametalmod.inventory.ContainerIronPlateMaker", "project.studio.manametalmod.tileentity.TileEntityIronPlateMaker", "project.studio.manametalmod.client.GuiIronPlateMaker", new int[]{2,3}),
        new Entry("project.studio.manametalmod.inventory.ContainerIronWroughtContainer", "project.studio.manametalmod.tileentity.TileEntityIronWroughtCrusher", "project.studio.manametalmod.client.GuiIronWroughtContainer", new int[]{2,3}),
        new Entry("project.studio.manametalmod.inventory.ContainerIronWroughtFurnace", "project.studio.manametalmod.tileentity.TileEntityIronWroughtFurnace", "project.studio.manametalmod.client.GuiIronWroughtFurnace", new int[]{2}),
        new Entry("project.studio.manametalmod.inventory.ContainerIronWroughtSteelF", "project.studio.manametalmod.tileentity.TileEntityIronWroughtSteelF", "project.studio.manametalmod.client.GuiIronWroughtSteelF", new int[]{2}),
        new Entry("project.studio.manametalmod.inventory.ContainerManaFuelMake", "project.studio.manametalmod.tileentity.TileEntityManaFuelMake", "project.studio.manametalmod.client.GuiManaFuelMake", new int[]{2,3}),
        new Entry("project.studio.manametalmod.inventory.ContainerManaFurnace", "project.studio.manametalmod.tileentity.TileEntityManaFurnace", "project.studio.manametalmod.client.GuiManaFurnace", new int[]{2}),
        new Entry("project.studio.manametalmod.inventory.ContainerManaMake1", "project.studio.manametalmod.tileentity.TileEntityManaMake1", "project.studio.manametalmod.client.GuiManaMake1", new int[]{2,3}),
        new Entry("project.studio.manametalmod.inventory.ContainerManaSF", "project.studio.manametalmod.tileentity.TileEntityManaSF", "project.studio.manametalmod.client.GuiManaSF", new int[]{2}),
        new Entry("project.studio.manametalmod.inventory.ContainerManaSFurnace", "project.studio.manametalmod.tileentity.TileEntityManaSFurnace", "project.studio.manametalmod.client.GuiManaSFurnace", new int[]{2,3}),
        new Entry("project.studio.manametalmod.inventory.ContainerMetalFurnace", "project.studio.manametalmod.tileentity.TileEntityFurnaceMetal", "project.studio.manametalmod.client.GuiMetalFurnace", new int[]{2}),
        new Entry("project.studio.manametalmod.inventory.ContainerMetalSeparator", "project.studio.manametalmod.tileentity.TileEntityMetalSeparator", "project.studio.manametalmod.client.GuiMetalSeparator", new int[]{2,3}),
        new Entry("project.studio.manametalmod.inventory.ContainerTimeFurnace", "project.studio.manametalmod.tileentity.TileEntityTimeFurnace", "project.studio.manametalmod.client.GuiTimeFurnace", new int[]{2}),
        new Entry("project.studio.manametalmod.inventory.ContainerManaGravityWell", "project.studio.manametalmod.tileentity.TileEntityManaGravityWell", "project.studio.manametalmod.client.GuiManaGravityWell", new int[]{12}),
        new Entry("project.studio.manametalmod.dark_magic.ContainerTileEntityDarkMain", "project.studio.manametalmod.dark_magic.TileEntityDarkMain", "project.studio.manametalmod.dark_magic.GuiTileEntityDarkMain", new int[]{0}),
        new Entry("project.studio.manametalmod.Lapuda.ContainerTileEntityBlueSky", "project.studio.manametalmod.Lapuda.TileEntityBlueSkyStele", "project.studio.manametalmod.Lapuda.GuiTileEntityBlueSky", new int[]{0}),
        new Entry("project.studio.manametalmod.world.thuliumempire.ContainerAncientEmpireCore", "project.studio.manametalmod.world.thuliumempire.TileEntityAncientEmpireCore", "project.studio.manametalmod.world.thuliumempire.GuiAncientEmpireCore", new int[]{0}),
        new Entry("project.studio.manametalmod.originmagic.ContainerOriginCrystalCore", "project.studio.manametalmod.originmagic.TileEntityOriginCrystalCore", "project.studio.manametalmod.originmagic.GuiOriginCrystalCore", new int[]{0}),
        new Entry("project.studio.manametalmod.inventory.ContainerManaCraftTable", "project.studio.manametalmod.tileentity.TileEntityManaCraftTable", "project.studio.manametalmod.client.GuiManaCraftTable", new int[]{9}),
        new Entry("project.studio.manametalmod.inventory.ContainerMetalCraftTable", "project.studio.manametalmod.tileentity.TileEntityMetalCraftTable", "project.studio.manametalmod.client.GuiMetalCraftTable", new int[]{9}),
        new Entry("project.studio.manametalmod.inventory.ContainerCastingTable", "project.studio.manametalmod.produce.casting.TileEntityCastingTable", "project.studio.manametalmod.client.GuiCastingTable", new int[]{22}),
        new Entry("project.studio.manametalmod.inventory.ContainerCookingTable", "project.studio.manametalmod.produce.cuisine.TileEntityCookingTable", "project.studio.manametalmod.client.GuiCookingTable", new int[]{7}),
        new Entry("project.studio.manametalmod.inventory.ContainerMagicPot", "project.studio.manametalmod.produce.cuisine.TileEntityPot", "project.studio.manametalmod.client.GuiMagicPot", new int[]{5}),
        new Entry("project.studio.manametalmod.inventory.ContainerCuttingBoard", "project.studio.manametalmod.produce.cuisine.TileEntityCuttingBoard", "project.studio.manametalmod.produce.cuisine.GuiCuttingBoard", new int[]{1}),
        new Entry("project.studio.manametalmod.inventory.ContainerSpinningWheel", "project.studio.manametalmod.produce.textile.TileEntitySpinningWheel", "project.studio.manametalmod.client.GuiSpinningWheel", new int[]{5}),
        new Entry("project.studio.manametalmod.inventory.ContainerBedrockMaker", "project.studio.manametalmod.tileentity.TileEntityBedrockMaker", "project.studio.manametalmod.client.GuiBedrpckMaker", new int[]{2}),
        new Entry("project.studio.manametalmod.inventory.ContainerTileEntityBase", "project.studio.manametalmod.tileentity.TileEntityBase", "project.studio.manametalmod.client.GuiTileEntityBase", new int[]{1}),
        new Entry("project.studio.manametalmod.inventory.ContainerPrayerAltar", "project.studio.manametalmod.tileentity.TileEntityPrayerAltar", "project.studio.manametalmod.client.GuiPrayerAltar", new int[]{0}),
        new Entry("project.studio.manametalmod.dark_magic.ContainerTileEntityDarkSteelBlast", "project.studio.manametalmod.dark_magic.TileEntityDarkSteelBlast", "project.studio.manametalmod.dark_magic.GuiTileEntityDarkSteelBlast", new int[]{1}),
        new Entry("project.studio.manametalmod.dark_magic.ContainerTileEntityDarkSteelFurnace", "project.studio.manametalmod.dark_magic.TileEntityDarkSteelFurnace", "project.studio.manametalmod.dark_magic.GuiTileEntityDarkSteelFurnace", new int[]{1}),
        new Entry("project.studio.manametalmod.dark_magic.ContainerTileEntityDarkItemMake", "project.studio.manametalmod.dark_magic.TileEntityDarkItemMake", "project.studio.manametalmod.dark_magic.GuiTileEntityDarkItemMake", new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36}),
        new Entry("project.studio.manametalmod.dark_magic.ContainerTileEntityDarkFission", "project.studio.manametalmod.dark_magic.TileEntityDarkFission", "project.studio.manametalmod.dark_magic.GuiTileEntityDarkFission", new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36}),
        new Entry("project.studio.manametalmod.inventory.ContainerOrePurification", "project.studio.manametalmod.tileentity.TileEntityOrePurification", "project.studio.manametalmod.client.GuiOrePurification", new int[]{1}),
        new Entry("project.studio.manametalmod.inventory.ContainerMetalReduction", "project.studio.manametalmod.tileentity.TileEntityMetalReduction", "project.studio.manametalmod.client.GuiMetalReduction", new int[]{19,20,21,22,23,24,25,26,27}),
        new Entry("project.studio.manametalmod.inventory.ContainerSieve", "project.studio.manametalmod.earlystrength.TileEntitySieve", "project.studio.manametalmod.client.GuiSieve", new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15}),
        new Entry("project.studio.manametalmod.produce.textile.ContainerClothesTailor", "project.studio.manametalmod.produce.textile.TileEntityClothesTailor", "project.studio.manametalmod.produce.textile.GuiClothesTailor", new int[]{5}),
        new Entry("project.studio.manametalmod.originmagic.ContainerPrimalForge", "project.studio.manametalmod.originmagic.TileEntityPrimalForge", "project.studio.manametalmod.originmagic.GuiPrimalForge", new int[]{45,46,47,48,49,50,51,52,53}),
        new Entry("project.studio.manametalmod.originmagic.ContainerMirrorPool", "project.studio.manametalmod.originmagic.TileEntityMirrorPool", "project.studio.manametalmod.originmagic.GuiMirrorPool", new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45}),
        new Entry("project.studio.manametalmod.produce.brewing.ContainerAdvancedBrewing", "project.studio.manametalmod.produce.brewing.TileEntityAdvancedBrewing", "project.studio.manametalmod.client.GuiAdvancedBrewing", new int[]{5,6,7}),
        new Entry("project.studio.manametalmod.produce.beekeeping.ContainerBeeBreeding", "project.studio.manametalmod.produce.beekeeping.TileEntityBeeBreeding", "project.studio.manametalmod.produce.beekeeping.GuiBeeBreeding", new int[]{2}),
        new Entry("project.studio.manametalmod.produce.beekeeping.ContainerBeecultivate", "project.studio.manametalmod.produce.beekeeping.TileEntityBeecultivate", "project.studio.manametalmod.produce.beekeeping.GuiBeecultivate", new int[]{18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35}),
        new Entry("project.studio.manametalmod.originmagic.ContainerEternalAnvil", "project.studio.manametalmod.originmagic.TileEntityEternalAnvil", "project.studio.manametalmod.originmagic.GuiEternalAnvil", new int[]{27}),
        new Entry("project.studio.manametalmod.inventory.ContainerRecycling", "project.studio.manametalmod.tileentity.TileEntityRecycling", "project.studio.manametalmod.client.GuiRecycling", new int[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53})
    };
    public final Entry entry;
    public final TileEntity tile;
    public final IInventory inventory;
    public final Container container;
    public final int variant;
    public OutputFixtures(Entry entry, World world, EntityPlayer player, int variant) throws Exception {
        this.entry=entry; this.variant=variant;
        tile=(TileEntity)Class.forName(entry.tile).newInstance();
        tile.func_145834_a(world); tile.field_145851_c=1; tile.field_145848_d=100; tile.field_145849_e=1;
        inventory=(IInventory)tile;
        world.func_147455_a(1,100,1,tile);
        if(tile instanceof TileEntityBase) {
            TileEntityBase base=(TileEntityBase)tile;
            base.Fuel=variant==1?FuelType.None:FuelType.values()[0];
            base.recipe=new ArrayList();
            base.TileName="outputcollect-fixture";
        }
        if(tile instanceof TileEntitySpinningWheel) ((TileEntitySpinningWheel)tile).blockID=variant;
        Constructor<?> ctor=Class.forName(entry.container).getConstructors()[0];
        Class<?>[] types=ctor.getParameterTypes(); Object[] args=new Object[types.length];
        int coordinate=0;
        for(int i=0;i<types.length;i++) {
            if(types[i]==InventoryPlayer.class)args[i]=player.field_71071_by;
            else if(types[i]==World.class)args[i]=world;
            else if(types[i]==int.class)args[i]=new int[]{1,100,1}[coordinate++];
            else if(types[i].isInstance(tile))args[i]=tile;
            else throw new AssertionError("Unhandled constructor "+ctor);
        }
        container=(Container)ctor.newInstance(args); container.field_75152_c=7;
        player.field_71070_bA=container;
    }
    public boolean output(int index) { for(int value:entry.outputs)if(value==index)return true; return false; }
    public ItemStack product(int index) {
        if(tile instanceof TileEntityAdvancedBrewing)return tagged(new ItemStack(ProduceCore.ItemAdvancedPotionE,1,0),"potion-"+index);
        if(tile instanceof TileEntityEternalAnvil) {
            TileEntityEternalAnvil t=(TileEntityEternalAnvil)tile;
            if(t.getSelectedRecipe()==null)throw new AssertionError("Eternal anvil recipe missing");
            return t.getSelectedRecipe().getResult();
        }
        return tagged(new ItemStack(Items.field_151045_i,7,index%2),"output-"+(index%2));
    }
    public void seed() {
        for(int i=0;i<inventory.func_70302_i_();i++)
            inventory.func_70299_a(i, output(i)? product(i):tagged(new ItemStack(Items.field_151055_y,1),"input-"+i));
    }
    public static ItemStack tagged(ItemStack stack,String name) {
        stack.func_151001_c(name);stack.func_77978_p().func_74783_a("FoxFoxTest",new int[]{4,8,15,16,23,42});return stack;
    }
    public static NBTTagCompound item(ItemStack stack) {
        NBTTagCompound tag=new NBTTagCompound();
        if(stack!=null) {
            tag.func_74778_a("Item",Item.field_150901_e.func_148750_c(stack.func_77973_b()));
            tag.func_74768_a("Count",stack.field_77994_a);tag.func_74768_a("Damage",stack.func_77960_j());
            if(stack.func_77942_o())tag.func_74782_a("Tag",stack.func_77978_p().func_74737_b());
        }
        return tag;
    }
    public static ItemStack item(NBTTagCompound tag) {
        if(!tag.func_74764_b("Item"))return null;
        Item type=(Item)Item.field_150901_e.func_82594_a(tag.func_74779_i("Item"));
        if(type==null)throw new AssertionError("Unknown item "+tag);
        ItemStack result=new ItemStack(type,tag.func_74762_e("Count"),tag.func_74762_e("Damage"));
        if(tag.func_74764_b("Tag"))result.func_77982_d((NBTTagCompound)tag.func_74775_l("Tag").func_74737_b());
        return result;
    }
    public static NBTTagCompound snapshot(Container c,EntityPlayer p) {
        NBTTagCompound tag=new NBTTagCompound();NBTTagList slots=new NBTTagList();
        for(Object value:c.field_75151_b)slots.func_74742_a(item(((Slot)value).func_75211_c()));
        tag.func_74782_a("Slots",slots);tag.func_74782_a("Cursor",item(p.field_71071_by.func_70445_o()));return tag;
    }
    public static void restore(Container c,EntityPlayer p,NBTTagCompound tag) {
        NBTTagList slots=tag.func_150295_c("Slots",10);
        for(int i=0;i<slots.func_74745_c();i++)c.func_75139_a(i).func_75215_d(item(slots.func_150305_b(i)));
        p.field_71071_by.func_70437_b(item(tag.func_74775_l("Cursor")));
    }
}
