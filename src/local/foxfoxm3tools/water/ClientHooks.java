package local.foxfoxm3tools.water;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;
import org.lwjgl.input.Keyboard;
import project.studio.manametalmod.watergame.*;

/** No commands, server hooks, direct inventory/world edits or teleportation. */
public final class ClientHooks {
    public final KeyBinding toggle=new KeyBinding("key.mmwater.toggle",Keyboard.KEY_F9,"key.categories.foxfoxm3tools");
    private final Minecraft mc=Minecraft.func_71410_x();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(new ThreadFactory() {
        public Thread newThread(Runnable r) { Thread t=new Thread(r,"Water-Maze-Plan");t.setDaemon(true);return t; }
    });
    private boolean toggleHeld;
    private Session session;
    private String lastStatus="水路自动已关闭";
    private enum Phase { PLAN, BREAK, BREAK_ACK, PICKUP, PLACE, SWAP_ACK, PLACE_ACK, FLOW }
    private static final class Session {
        World world; EntityClientPlayerMP player;
        int cx,cy,cz,ox,by,oz,slot,originalSlot,age,total,index,expectedCount;
        long seed;
        Navigator nav;
        Future<Blueprint> future;
        Blueprint plan;
        Blueprint.Cell current;
        final List<Blueprint.Cell> repairs=new ArrayList<Blueprint.Cell>();
        final List<Blueprint.Cell> placements=new ArrayList<Blueprint.Cell>();
        Phase phase=Phase.PLAN;
        String status="正在计算水路";
    }
    public boolean active() { return session!=null; }
    public String status() { return session==null?lastStatus:session.status; }
    private void message(String text) {
        if (mc.field_71439_g!=null) mc.field_71439_g.func_145747_a(new ChatComponentText("§b[水路自动] §r"+text));
    }
    public void stop(String reason) {
        Session s=session;
        if (s!=null) {
            s.nav.reset();
            if (s.future!=null) s.future.cancel(true);
            // Keep a user's explicit slot change; otherwise restore their selected slot.
            if (mc.field_71439_g==s.player && s.player.field_71071_by.field_70461_c==s.slot) select(s,s.originalSlot);
        }
        session=null;lastStatus=reason;message(reason);
    }
    public void start() {
        if (session!=null || mc.field_71439_g==null || mc.field_71441_e==null || mc.field_71462_r!=null) return;
        if (mc.field_71439_g.field_71075_bZ.field_75098_d) { message("请在生存模式进行试炼；创造模式右键会更改管道锁定状态。");return; }
        TileEntityWaterGameCore nearest=null;double distance=24*24;
        // The core has canUpdate()==false, so it is absent from the world's ticking
        // tile list. Read loaded chunk tile maps, including non-ticking cores.
        List<Object> tiles=new ArrayList<Object>();
        int pcx=(int)Math.floor(mc.field_71439_g.field_70165_t)>>4;
        int pcz=(int)Math.floor(mc.field_71439_g.field_70161_v)>>4;
        for (int cx=pcx-2;cx<=pcx+2;cx++) for (int cz=pcz-2;cz<=pcz+2;cz++)
            if (mc.field_71441_e.func_72863_F().func_73149_a(cx,cz))
                tiles.addAll(mc.field_71441_e.func_72964_e(cx,cz).field_150816_i.values());
        for (Object value:tiles) {
            if (!(value instanceof TileEntityWaterGameCore)) continue;
            TileEntityWaterGameCore tile=(TileEntityWaterGameCore)value;
            double d=mc.field_71439_g.func_70092_e(tile.field_145851_c+.5,tile.field_145848_d+.5,tile.field_145849_e+4.5);
            if (d<distance) { nearest=tile;distance=d; }
        }
        if (nearest==null || !nearest.generated) { message("请先开启魔法试炼、右键装置生成水路迷宫，再靠近迷宫按 F9（或自定义键）。");return; }
        final long seed=nearest.puzzleSeed;
        Session s=new Session();s.world=mc.field_71441_e;s.player=mc.field_71439_g;
        s.cx=nearest.field_145851_c;s.cy=nearest.field_145848_d;s.cz=nearest.field_145849_e;s.seed=seed;
        s.ox=nearest.getBoardOriginX();s.by=nearest.getBoardOriginY();s.oz=nearest.getBoardOriginZ();
        s.originalSlot=s.slot=s.player.field_71071_by.field_70461_c;
        s.nav=new Navigator(mc,s.ox,s.by,s.oz);
        s.future=worker.submit(new Callable<Blueprint>() { public Blueprint call() throws Exception { return new Blueprint(seed); } });
        session=s;message("已开启：自动回收、补放管道并等待通水。再次按开关键或手动移动可停止。");
    }
    private void handleKey() {
        boolean pressed=false;while (toggle.func_151468_f()) pressed=true;
        boolean fire=pressed&&!toggleHeld;toggleHeld=toggle.func_151470_d();
        if (fire && toggle.func_151463_i()!=0 && mc.field_71462_r==null) {
            if (session==null) start();else stop("已手动停止水路自动。");
        }
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase!=TickEvent.Phase.END) return;
        try { handleKey();if (session!=null) advance(); }
        catch (Exception ex) { fail(ex); }
        catch (LinkageError ex) { fail(ex); }
    }
    private void fail(Throwable ex) {
        if (session!=null && session.current!=null) System.err.println("[Water-Maze-Auto] target="+
                x(session,session.current)+","+y(session,session.current)+","+z(session,session.current)+" phase="+session.phase);
        ex.printStackTrace();
        stop(ex instanceof IllegalStateException ? ex.getMessage() : "迷宫数据或版本不兼容，已停止；详情见客户端日志。");
    }
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Text event) {
        if (session!=null) event.left.add("§b水路自动：§f"+status());
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if (session!=null && event.world==session.world) stop("已离开迷宫所在世界，水路自动停止。");
    }
    private void advance() throws Exception {
        Session s=session;
        if (mc.field_71441_e!=s.world || mc.field_71439_g!=s.player || !s.player.func_70089_S()) { stop("玩家或世界已变化，水路自动停止。");return; }
        if (mc.field_71462_r!=null || s.player.func_70093_af() || s.player.func_70608_bn()
                || s.player.field_71075_bZ.field_75098_d || s.player.field_70154_o!=null) { stop("已打开界面或玩家状态改变，水路自动停止。");return; }
        if (s.player.field_71071_by.field_70461_c!=s.slot || manualInput()) { stop("检测到手动操作，水路自动停止。");return; }
        if (++s.total>12000 || ++s.age>1200) { stop("操作超时，水路自动停止；请检查材料、路径及服务器响应。");return; }
        TileEntity tile=s.world.func_147438_o(s.cx,s.cy,s.cz);
        if (!(tile instanceof TileEntityWaterGameCore) || ((TileEntityWaterGameCore)tile).puzzleSeed!=s.seed) {
            stop("迷宫已更换或卸载，水路自动停止。");return;
        }
        if (s.phase==Phase.PLAN) {
            if (!s.future.isDone()) return;
            s.plan=s.future.get();s.future=null;
            validate(s);if (session==null) return;
            s.phase=Phase.BREAK;s.age=0;return;
        }
        Blueprint.Cell end=s.plan.cells.get(s.plan.cells.size()-1);
        TileEntityWaterGame finish=water(s,end);
        if (finish!=null && finish.type==WaterGameType.end && finish.hasWater) { stop("喷泉已通水！魔法试炼水路完成。");return; }
        if (s.phase==Phase.FLOW) {
            if (s.age>600) stop("管道已铺设，但 30 秒内未确认喷泉通水，已停止；请检查服务器状态。");
            return;
        }
        if (s.phase==Phase.BREAK) {
            if (s.index>=s.repairs.size()) { s.phase=Phase.PLACE;s.index=0;s.age=0;return; }
            s.current=s.repairs.get(s.index);
            TileEntityWaterGame w=water(s,s.current);
            require(w!=null && w.canMove && w.type==s.current.type,"待回收管道已变化，停止以避免误拆。");
            require(hasRoom(s,s.current.block()),"背包没有空间回收管道，请整理背包后重试。");
            s.status="回收方向错误的管道 "+(s.index+1)+"/"+s.repairs.size();
            if (!approach(s,s.current)) return;
            s.expectedCount=count(s,s.current.block())+1;
            s.player.field_71174_a.func_147297_a(new C07PacketPlayerDigging(0,x(s,s.current),y(s,s.current),z(s,s.current),1));
            s.phase=Phase.BREAK_ACK;s.age=0;return;
        }
        if (s.phase==Phase.BREAK_ACK) {
            if (s.world.func_147437_c(x(s,s.current),y(s,s.current),z(s,s.current))) { s.phase=Phase.PICKUP;s.age=0; }
            else if (s.age>100) stop("服务器未确认拆下管道，已停止。");
            return;
        }
        if (s.phase==Phase.PICKUP) {
            if (count(s,s.current.block())>=s.expectedCount) { s.nav.reset();s.index++;s.phase=Phase.BREAK;s.age=0;return; }
            s.status="拾取回收的管道";
            collect(s);
            if (s.age>300) stop("未拾取到回收的管道，已停止；请捡回材料后重新开启。");
            return;
        }
        if (s.phase==Phase.PLACE) {
            if (s.index>=s.placements.size()) { s.nav.reset();s.phase=Phase.FLOW;s.age=0;s.status="铺设完成，等待喷泉通水";return; }
            s.current=s.placements.get(s.index);
            if (matches(s,s.current)) { s.index++;s.age=0;return; }
            require(s.world.func_147437_c(x(s,s.current),y(s,s.current),z(s,s.current)),"待放置位置被占用，已停止。");
            s.status="补放管道 "+(s.index+1)+"/"+s.placements.size();
            if (!approach(s,s.current)) return;
            int itemSlot=findItem(s,s.current.block());
            require(itemSlot>=0,"背包缺少 "+s.current.type+" 管道；请取回试炼材料后重新开启。");
            if (itemSlot>=9) {
                mc.field_71442_b.func_78753_a(s.player.field_71069_bz.field_75152_c,itemSlot,s.slot,2,s.player);
                s.phase=Phase.SWAP_ACK;s.age=0;return;
            }
            select(s,itemSlot);place(s);return;
        }
        if (s.phase==Phase.SWAP_ACK) {
            ItemStack held=s.player.func_71045_bC();
            if (s.age>=5 && held!=null && held.func_77973_b()==Item.func_150898_a(s.current.block())) { place(s);return; }
            if (s.age>100) stop("背包物品同步超时，已停止。");
            return;
        }
        if (s.phase==Phase.PLACE_ACK) {
            if (s.age>=5 && matches(s,s.current)) { s.index++;s.phase=Phase.PLACE;s.age=0;return; }
            if (s.age>100) stop("放置未获服务器确认，已停止；请确认试炼仍在进行、材料可用且位置未被占用。");
        }
    }
    private boolean manualInput() {
        KeyBinding[] keys={mc.field_71474_y.field_74351_w,mc.field_71474_y.field_74368_y,mc.field_71474_y.field_74370_x,
                mc.field_71474_y.field_74366_z,mc.field_71474_y.field_74314_A,mc.field_71474_y.field_74311_E,
                mc.field_71474_y.field_74312_F,mc.field_71474_y.field_74313_G};
        for (KeyBinding key:keys) if (key.func_151463_i()!=toggle.func_151463_i() && Navigator.physicallyDown(key)) return true;
        return false;
    }
    private void validate(Session s) {
        for (int dx=0;dx<8;dx++) for (int dz=0;dz<8;dz++) {
            require(s.world.func_72899_e(s.ox+dx,s.by,s.oz+dz),"迷宫区块未加载完整，请靠近后重试。");
            require(s.world.func_147439_a(s.ox+dx,s.by,s.oz+dz)==WaterGameCore.rock,"迷宫地基与当前版本不匹配，已停止。");
        }
        for (Blueprint.Cell c:s.plan.cells) {
            if (matches(s,c)) continue;
            require(c.editable,"固定管道与生成数据不一致；可能正在同步或服务器版本不同，请稍后重试。");
            if (!s.world.func_147437_c(x(s,c),y(s,c),z(s,c))) {
                TileEntityWaterGame w=water(s,c);
                require(w!=null && w.canMove && w.type==c.type,"路径被其他方块或固定管道占用，已停止。");
                s.repairs.add(c);
            }
            s.placements.add(c);
        }
        // Preserve all fixed blocks, including the lower receiver beneath a waterfall.
        WaterGamePuzzleGenerator.Result p=s.plan.puzzle;
        TileEntity receiver=s.world.func_147438_o(s.ox+p.dropX,s.by+1,s.oz+p.dropZ);
        require(receiver instanceof TileEntityWaterGame && ((TileEntityWaterGame)receiver).type==WaterGameType.X,
                "落水口下方接水管道不完整，已停止。");
        Map<Block,Integer> need=new HashMap<Block,Integer>();
        for (Blueprint.Cell c:s.placements) need.put(c.block(),need.containsKey(c.block())?need.get(c.block())+1:1);
        for (Blueprint.Cell c:s.repairs) need.put(c.block(),need.get(c.block())-1);
        for (Map.Entry<Block,Integer> e:need.entrySet()) require(count(s,e.getKey())>=e.getValue(),
                "管道材料不足："+e.getKey().func_149732_F()+" 需要 "+e.getValue()+" 个，背包中有 "+count(s,e.getKey())+" 个。请收齐材料后再开启。");
    }
    private boolean approach(final Session s,final Blueprint.Cell c) {
        final double tx=x(s,c)+.5,tz=z(s,c)+.5;
        final double ty=y(s,c);
        return s.nav.move(new Navigator.Goal() {
            public boolean accepts(double px,double feet,double pz) {
                double dx=px-tx,dz=pz-tz;
                double eye=feet+s.player.func_70047_e();
                double reach=Math.min(4.45,mc.field_71442_b.func_78757_d()-.05);
                // Placement must not intersect the player's body, even after walking.
                return dx*dx+dz*dz+(eye-ty)*(eye-ty)<reach*reach
                        && dx*dx+dz*dz+(feet-ty)*(feet-ty)<30
                        && (Math.abs(dx)>.85 || Math.abs(dz)>.85 || feet>=y(s,c)+1 || feet+1.8<=y(s,c));
            }
        });
    }
    private void collect(final Session s) {
        EntityItem closest=null;double best=Double.MAX_VALUE;
        for (Object value:s.world.field_72996_f) {
            if (!(value instanceof EntityItem)) continue;
            EntityItem entity=(EntityItem)value;
            ItemStack stack=entity.func_92059_d();
            if (entity.field_70128_L || stack==null || stack.func_77973_b()!=Item.func_150898_a(s.current.block())
                    || Math.abs(entity.field_70165_t-x(s,s.current)-.5)>4
                    || Math.abs(entity.field_70161_v-z(s,s.current)-.5)>4
                    || entity.field_70163_u<s.by || entity.field_70163_u>y(s,s.current)+3) continue;
            double d=s.player.func_70068_e(entity);
            if (d<best) { closest=entity;best=d; }
        }
        // Drops can bounce off a two-high pillar and land on a different level.
        // Follow the synchronized item, not the location of the removed block.
        if (closest==null) { s.nav.reset();return; }
        final double tx=closest.field_70165_t,ty=closest.field_70163_u,tz=closest.field_70161_v;
        s.nav.move(new Navigator.Goal() {
            public boolean accepts(double px,double feet,double pz) {
                double dx=px-tx,dz=pz-tz;
                return dx*dx+dz*dz<.81 && feet<=ty+.3 && feet+1.8>=ty;
            }
        });
    }
    private void place(Session s) {
        require(s.world.func_147439_a(x(s,s.current),y(s,s.current)-1,z(s,s.current)) instanceof BlockWaterGame,
                "管道下方没有支撑，已停止。");
        require(s.world.func_147437_c(x(s,s.current),y(s,s.current),z(s,s.current)),"放置位置已变化，已停止。");
        float yaw=s.current.meta*90F;
        s.player.field_70177_z=yaw;s.player.field_70125_A=65;
        s.player.field_71174_a.func_147297_a(new C03PacketPlayer.C05PacketPlayerLook(yaw,65,s.player.field_70122_E));
        s.player.field_71174_a.func_147297_a(new C08PacketPlayerBlockPlacement(x(s,s.current),y(s,s.current)-1,z(s,s.current),1,
                s.player.func_71045_bC(),.5F,1F,.5F));
        s.phase=Phase.PLACE_ACK;s.age=0;
    }
    private void select(Session s,int slot) {
        s.player.field_71071_by.field_70461_c=slot;s.slot=slot;
        s.player.field_71174_a.func_147297_a(new C09PacketHeldItemChange(slot));
    }
    private int findItem(Session s,Block block) {
        Item item=Item.func_150898_a(block);
        for (int i=0;i<36;i++) {
            ItemStack stack=s.player.field_71071_by.field_70462_a[i];
            if (stack!=null && stack.field_77994_a>0 && stack.func_77973_b()==item) return i;
        }
        return -1;
    }
    private int count(Session s,Block block) {
        int count=0;Item item=Item.func_150898_a(block);
        for (ItemStack stack:s.player.field_71071_by.field_70462_a) if (stack!=null && stack.func_77973_b()==item) count+=stack.field_77994_a;
        return count;
    }
    private boolean hasRoom(Session s,Block block) {
        for (ItemStack stack:s.player.field_71071_by.field_70462_a)
            if (stack==null || stack.func_77973_b()==Item.func_150898_a(block) && stack.field_77994_a<stack.func_77976_d()) return true;
        return false;
    }
    private int x(Session s,Blueprint.Cell c) { return s.ox+c.x; }
    private int y(Session s,Blueprint.Cell c) { return s.by+c.y; }
    private int z(Session s,Blueprint.Cell c) { return s.oz+c.z; }
    private TileEntityWaterGame water(Session s,Blueprint.Cell c) {
        TileEntity t=s.world.func_147438_o(x(s,c),y(s,c),z(s,c));
        return t instanceof TileEntityWaterGame?(TileEntityWaterGame)t:null;
    }
    private boolean matches(Session s,Blueprint.Cell c) {
        TileEntityWaterGame t=water(s,c);
        return t!=null && c.matches(t.type,s.world.func_72805_g(x(s,c),y(s,c),z(s,c)));
    }
    private void require(boolean value,String message) { if (!value) throw new IllegalStateException(message); }
}
