package local.foxfoxm3tools.archeology;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import project.studio.manametalmod.archeology.ContainerArcheology;
import project.studio.manametalmod.archeology.GuiArcheology;
import project.studio.manametalmod.archeology.IArcheologyTool;
import project.studio.manametalmod.archeology.IArcheologyType;
import project.studio.manametalmod.archeology.ObjectArcheology;
import project.studio.manametalmod.archeology.ObjectArcheologyType;
import project.studio.manametalmod.archeology.TileEntityArcheology;
import project.studio.manametalmod.network.MessageArcheology;
import project.studio.manametalmod.network.PacketHandlerMana;

/** Client-side controller for the four-tool archaeology board. */
public final class ClientHooks {
    private static final int TIMEOUT = 200;
    private static final int SWITCH_WAIT = 3;
    private EntityPlayer owner;
    private Session session;

    private static final class AutoButton extends GuiButton {
        AutoButton(int x, int y) { super(27961, x, y, 58, 18, ""); }
    }

    private static final class Session {
        final GuiArcheology gui;
        final TileEntityArcheology tile;
        AutoButton button;
        boolean enabled;
        boolean pending;
        boolean timedOut;
        int waited;
        int switchWait;
        long pendingState;
        boolean returningTool;
        int returnWait;

        Session(GuiArcheology gui, TileEntityArcheology tile) {
            this.gui = gui;
            this.tile = tile;
        }
    }

    private static final class Action {
        final int slot;
        final IArcheologyType tool;

        Action(int slot, IArcheologyType tool) {
            this.slot = slot;
            this.tool = tool;
        }
    }

    private static TileEntityArcheology tile(GuiScreen gui) {
        if (!(gui instanceof GuiArcheology) || !(gui instanceof GuiContainer)) return null;
        if (!(((GuiContainer)gui).field_147002_h instanceof ContainerArcheology)) return null;
        return ((ContainerArcheology)((GuiContainer)gui).field_147002_h).tile;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unchecked")
    public void init(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiArcheology)) return;
        GuiArcheology gui = (GuiArcheology)event.gui;
        TileEntityArcheology tile = tile(gui);
        if (tile == null) return;
        checkOwner();
        if (session == null || session.gui != gui) session = new Session(gui, tile);
        for (Iterator<?> it = event.buttonList.iterator(); it.hasNext();) {
            if (it.next() instanceof AutoButton) it.remove();
        }
        int left = (gui.field_146294_l - 256) / 2;
        int top = (gui.field_146295_m - 256) / 2;
        session.button = new AutoButton(left + 194, top + 153);
        event.buttonList.add(session.button);
        label();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void click(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.button instanceof AutoButton)) return;
        event.setCanceled(true);
        if (session == null || session.gui != event.gui || session.timedOut) return;
        session.enabled = !session.enabled;
        session.pending = false;
        session.waited = 0;
        label();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void open(GuiOpenEvent event) {
        if (session == null || session.gui == event.gui) return;
        session = null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        checkOwner();
        Minecraft mc = Minecraft.func_71410_x();
        if (session == null) return;
        TileEntityArcheology te = session.tile;
        if (owner == null || mc.field_71441_e == null || mc.field_71462_r != session.gui
                || te == null || te.func_145831_w() != mc.field_71441_e || te.func_145837_r()) {
            session = null;
            return;
        }
        if (!session.enabled) return;
        try {
            if (session.switchWait > 0) {
                --session.switchWait;
                return;
            }
            if (session.returningTool) {
                ItemStack cursor = owner.field_71071_by.func_70445_o();
                if (cursor == null) {
                    session.returningTool = false;
                    session.enabled = false;
                    mc.func_147108_a(null);
                } else if (++session.returnWait >= TIMEOUT) {
                    fail("timeout");
                }
                return;
            }
            long state = fingerprint(te);
            if (session.pending) {
                if (state == session.pendingState) {
                    if (++session.waited >= TIMEOUT) fail("timeout");
                    return;
                }
                session.pending = false;
                session.waited = 0;
            }
            if (te.obj == null || te.obj.length < 16) return;
            if (complete(te)) {
                if (returnTool(mc)) return;
                if (owner.field_71071_by.func_70445_o() != null) {
                    session.enabled = false;
                    owner.func_145747_a(new ChatComponentText(tr("prefix") + tr("no_space")));
                    return;
                }
                session.enabled = false;
                owner.func_145747_a(new ChatComponentText(tr("prefix") + tr("done")));
                mc.func_147108_a(null);
                return;
            }
            Action action = next(te);
            if (action == null) return;
            if (!ensureCursorTool(mc, action.tool)) return;
            int power = action.tool == IArcheologyType.hammer
                    ? Math.max(0, Math.min(100, (int)(session.gui.power * 0.5f))) : 0;
            if (action.tool == IArcheologyType.hammer && power <= 0) return;
            session.pendingState = state;
            session.pending = true;
            session.waited = 0;
            PacketHandlerMana.INSTANCE.sendToServer(new MessageArcheology(action.tool.ordinal(),
                    te.field_145851_c, te.field_145848_d, te.field_145849_e,
                    false, action.slot, power, 0));
        } catch (RuntimeException error) {
            fail("incompatible");
            error.printStackTrace();
        } catch (LinkageError error) {
            fail("incompatible");
            error.printStackTrace();
        }
        label();
    }

    private static Action next(TileEntityArcheology tile) {
        for (int slot = 0; slot < 16; ++slot) {
            ObjectArcheology obj = tile.obj[slot];
            if (obj == null || obj.levels == null || obj.levels.length < 3) continue;
            ObjectArcheologyType top = null;
            for (int layer = 2; layer >= 0; --layer) {
                ObjectArcheologyType value = obj.levels[layer];
                if (value != null && value != ObjectArcheologyType.none) {
                    top = value;
                    break;
                }
            }
            if (top == null) {
                if (obj.hasItem) return new Action(slot, IArcheologyType.magnifier);
            } else if (top == ObjectArcheologyType.stone || top == ObjectArcheologyType.andesite
                    || top == ObjectArcheologyType.diorite) {
                return new Action(slot, IArcheologyType.hammer);
            } else if (top == ObjectArcheologyType.gravel || top == ObjectArcheologyType.sand) {
                return new Action(slot, IArcheologyType.brush);
            } else if (top == ObjectArcheologyType.web) {
                return new Action(slot, IArcheologyType.scissors);
            }
        }
        return null;
    }

    private static boolean complete(TileEntityArcheology tile) {
        for (int slot = 0; slot < 16; ++slot) {
            ObjectArcheology obj = tile.obj[slot];
            if (obj == null || obj.hasItem || obj.levels == null || obj.levels.length < 3) return false;
            for (int layer = 0; layer < 3; ++layer) {
                if (obj.levels[layer] != null && obj.levels[layer] != ObjectArcheologyType.none) return false;
            }
        }
        return true;
    }

    private boolean ensureCursorTool(Minecraft mc, IArcheologyType type) {
        ItemStack cursor = owner.field_71071_by.func_70445_o();
        if (cursor != null && !(cursor.func_77973_b() instanceof IArcheologyTool)) {
            fail("cursor");
            return false;
        }
        if (cursor != null) {
            IArcheologyTool current = (IArcheologyTool)cursor.func_77973_b();
            if (current.getType(cursor) == type && current.getToolDamage(cursor) < current.maxToolUse(cursor)) return true;
        }
        int inventorySlot = findTool(owner, type);
        if (inventorySlot < 0) {
            fail("tool");
            return false;
        }
        clickInventorySlot(mc, inventorySlot);
        session.switchWait = SWITCH_WAIT;
        return false;
    }

    private int findTool(EntityPlayer player, IArcheologyType type) {
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack item = player.field_71071_by.func_70301_a(slot);
            if (item == null || !(item.func_77973_b() instanceof IArcheologyTool)) continue;
            IArcheologyTool tool = (IArcheologyTool)item.func_77973_b();
            if (tool.getType(item) == type && tool.getToolDamage(item) < tool.maxToolUse(item)) return slot;
        }
        return -1;
    }

    private boolean returnTool(Minecraft mc) {
        ItemStack cursor = owner.field_71071_by.func_70445_o();
        if (cursor == null || !(cursor.func_77973_b() instanceof IArcheologyTool)) return false;
        int empty = findEmptyInventorySlot(owner);
        if (empty < 0) return false;
        clickInventorySlot(mc, empty);
        session.returningTool = true;
        session.returnWait = 0;
        session.switchWait = SWITCH_WAIT;
        return true;
    }

    private void clickInventorySlot(Minecraft mc, int inventorySlot) {
        int containerSlot = inventorySlot >= 9 ? inventorySlot - 9 : inventorySlot + 27;
        mc.field_71442_b.func_78753_a(owner.field_71070_bA.field_75152_c,
                containerSlot, 0, 0, owner);
    }

    private static int findEmptyInventorySlot(EntityPlayer player) {
        for (int slot = 0; slot < 36; ++slot) {
            if (player.field_71071_by.func_70301_a(slot) == null) return slot;
        }
        return -1;
    }

    private static long fingerprint(TileEntityArcheology tile) {
        long hash = 1469598103934665603L;
        if (tile.obj == null) return hash ^ 1L;
        for (int slot = 0; slot < 16; ++slot) {
            ObjectArcheology obj = slot < tile.obj.length ? tile.obj[slot] : null;
            hash ^= obj == null ? 0 : 1;
            hash *= 1099511628211L;
            if (obj == null) continue;
            hash ^= obj.hasItem ? 1 : 2;
            hash *= 1099511628211L;
            for (int layer = 0; layer < 3; ++layer) {
                ObjectArcheologyType value = obj.levels != null && layer < obj.levels.length
                        ? obj.levels[layer] : null;
                hash ^= value == null ? 0 : value.ordinal() + 1;
                hash *= 1099511628211L;
                int hp = obj.objectHP != null && layer < obj.objectHP.length ? obj.objectHP[layer] : 0;
                hash ^= hp;
                hash *= 1099511628211L;
            }
        }
        return hash;
    }

    private void fail(String key) {
        session.enabled = false;
        session.timedOut = "timeout".equals(key);
        if (owner != null) owner.func_145747_a(new ChatComponentText(tr("prefix") + tr(key)));
        label();
    }

    private void checkOwner() {
        EntityPlayer player = Minecraft.func_71410_x().field_71439_g;
        if (owner != player) {
            owner = player;
            session = null;
        }
    }

    private void label() {
        if (session != null && session.button != null) {
            session.button.field_146126_j = tr(session.timedOut ? "waiting" : session.enabled ? "on" : "off");
            session.button.field_146124_l = !session.timedOut;
        }
    }

    private static String tr(String key) {
        return StatCollector.func_74838_a("archeologyauto." + key);
    }
}
