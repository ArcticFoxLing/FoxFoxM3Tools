package local.foxfoxm3tools.chess;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;
import org.lwjgl.input.Keyboard;
import project.studio.manametalmod.chess.ChessCore;
import project.studio.manametalmod.chess.TileEntityChessAI;
import project.studio.manametalmod.chess.TileEntityChessBase;
import project.studio.manametalmod.core.Pos;

/** Only reads client world state. All game actions are ordinary block-use packets. */
public final class ClientHooks {
    public final KeyBinding toggle=new KeyBinding("key.mmchess.toggle",Keyboard.KEY_F8,"key.categories.foxfoxm3tools");
    private boolean toggleHeld;
    private static final int STABLE_TICKS=8, SELECT_TIMEOUT=120, MOVE_TIMEOUT=240, AI_TIMEOUT=1200;
    private static final double REACH_SQUARED=36.0;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(new ThreadFactory() {
        public Thread newThread(Runnable r) { Thread t=new Thread(r,"ManaMetal-Chess-Search");t.setDaemon(true);return t; }
    });
    private final Minecraft mc=Minecraft.func_71410_x();
    private Session session;
    private String lastStatus="自动下棋已关闭";
    private enum Phase { THINK, SELECT, REPLY }
    private static final class Session {
        World world; EntityPlayer player; int x,y,z,slot,age,stable,moved;
        Phase phase=Phase.THINK;
        Position position,afterWhite;
        byte[] observed;
        int move=-1;
        boolean acknowledged;
        Future<Engine.Result> search;
        final List<byte[]> history=new ArrayList<byte[]>();
        String status="等待棋盘稳定";
    }
    public void toggle() { if(session==null) start(); else stop("已关闭自动下棋。"); }
    public String status() { return session==null?lastStatus:session.status+"（已走 "+session.moved+" 步）"; }
    public void message(String text) {
        if(mc.field_71439_g!=null) mc.field_71439_g.func_145747_a(new ChatComponentText("§b[智慧自动] §r"+text));
    }
    public void stop(String reason) {
        if(session!=null&&session.search!=null) session.search.cancel(true);
        session=null;lastStatus=reason;message(reason);
    }
    public void start() {
        if(session!=null) { message(status());return; }
        if(mc.field_71439_g==null||mc.field_71441_e==null) { message("请先进入世界。");return; }
        try {
            Pos core=boundCore();
            if(core==null||!(core.getPosTileEntity(mc.field_71441_e) instanceof TileEntityChessAI)) {
                message("请先右键棋盘的对弈装置领取棋杖，并把该棋杖拿在手中。");return;
            }
            Session s=new Session();s.world=mc.field_71441_e;s.player=mc.field_71439_g;
            s.x=core.X;s.y=core.Y;s.z=core.Z;s.slot=mc.field_71439_g.field_71071_by.field_70461_c;
            byte[] board=readBoard(s);
            if(board==null) { message("棋盘尚未完整加载，请靠近棋盘后再开启。");return; }
            // TileEntityChessAI does not synchronize its turn/castling/en-passant fields.
            // A fresh board is the only unambiguous entry point for a client-only addon.
            if(!Position.initial().sameBoard(board)) {
                message("请在新一局、白方尚未走第一步时开启，以便准确跟踪回合和特殊走法。");return;
            }
            if(!wholeBoardInReach(s)) { message("请站到棋盘中央再开启（所有格子须在 6 格内）。");return; }
            s.position=Position.initial();s.observed=board;s.history.add(board.clone());session=s;
            message("已开启：自动执白。保持手持棋杖；再按一次开关键可停止。");
        } catch(RuntimeException error) { failure(error); }
        catch(LinkageError error) { failure(error); }
    }
    private void handleToggle() {
        // Drain queued presses even in menus. Holding a key, including keyboard
        // auto-repeat, must not repeatedly start and stop an in-progress game.
        boolean pressed=false;
        while(toggle.func_151468_f()) pressed=true;
        boolean trigger=pressed&&!toggleHeld;
        toggleHeld=toggle.func_151470_d();
        if(trigger&&toggle.func_151463_i()!=Keyboard.KEY_NONE&&mc.field_71462_r==null
                &&mc.field_71439_g!=null&&mc.field_71441_e!=null) toggle();
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if(session!=null&&event.world==session.world&&event.world.field_72995_K) stop("已离开棋盘所在世界，自动下棋停止。");
    }
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Text event) {
        if(session!=null) event.left.add("§b智慧自动：§f"+status());
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        handleToggle();
        if(session==null) return;
        try { advance(); } catch(Exception error) { failure(error); } catch(LinkageError error) { failure(error); }
    }
    private void failure(Throwable error) {
        System.err.println("[ManaMetal-Chess-Auto] Stopped after an incompatible state");error.printStackTrace();
        stop("发生兼容或计算异常，已停止。详情见客户端日志。");
    }
    private void advance() throws Exception {
        Session s=session;
        if(mc.field_71441_e!=s.world||mc.field_71439_g!=s.player||!s.player.func_70089_S()) { stop("玩家或世界已变化，自动下棋停止。");return; }
        Pos core=boundCore();
        if(core==null||core.X!=s.x||core.Y!=s.y||core.Z!=s.z||mc.field_71439_g.field_71071_by.field_70461_c!=s.slot) {
            stop("已切换物品或棋杖，自动下棋停止。");return;
        }
        if(!(core.getPosTileEntity(s.world) instanceof TileEntityChessAI)) { stop("对弈装置已消失，自动下棋停止。");return; }
        if(mc.field_71462_r!=null) {
            if(mc.field_71462_r instanceof GuiChat) return;
            stop("已打开其他界面，自动下棋停止。");return;
        }
        if(s.player.func_70093_af()||s.player.func_70608_bn()||!wholeBoardInReach(s)) {
            stop("请保持站立并留在棋盘中央，自动下棋已停止。");return;
        }
        byte[] current=readBoard(s);
        if(current==null) { stop("棋盘不完整或区块已卸载，自动下棋停止。");return; }
        s.age++;
        if(!Arrays.equals(s.observed,current)) { s.observed=current;s.stable=0; }
        else s.stable++;
        if(s.stable<STABLE_TICKS) return;
        if(s.phase==Phase.REPLY) { reply(s,current);return; }
        if(!s.position.sameBoard(current)) { stop("棋盘被其他操作改变，已停止以避免误走。");return; }
        if(s.phase==Phase.SELECT) {
            if(s.age>SELECT_TIMEOUT) { stop("选子确认超时，已停止；请检查距离和服务器响应。");return; }
            if(s.age>=4&&selected(s)) {
                int target=Position.to(s.move);
                click(s,target,current[target]<0?s.y:s.y-1);
                s.afterWhite=s.position.play(s.move);s.phase=Phase.REPLY;s.age=0;s.acknowledged=false;
                s.status="等待落子确认："+Position.name(s.move);
            }
            return;
        }
        if(s.search==null) {
            final Position position=s.position;
            final List<byte[]> history=new ArrayList<byte[]>(s.history);
            s.search=worker.submit(new Callable<Engine.Result>() { public Engine.Result call() { return new Engine().search(position,1500,history); } });
            s.status="正在计算下一步";return;
        }
        if(!s.search.isDone()) return;
        Engine.Result result=s.search.get();s.search=null;
        if(result.move<0) { stop("当前没有可走棋步，自动下棋停止。");return; }
        s.move=result.move;
        if(wouldAccidentallyCastle(s)) { stop("棋杖正选中王，直接选车可能触发易位；请先手动清除选择，再从新局开启。");return; }
        mc.field_71439_g.field_71174_a.func_147297_a(new C09PacketHeldItemChange(s.slot));
        click(s,Position.from(s.move),s.y);
        s.phase=Phase.SELECT;s.age=0;s.status="选择棋子："+Position.name(s.move)+"（深度 "+result.depth+"）";
    }
    private void reply(Session s,byte[] current) {
        if(s.afterWhite.sameBoard(current)) {
            if(s.afterWhite.king(-1)<0) { stop("黑王已被吃掉！本局自动下棋完成。");return; }
            if(!s.acknowledged) { s.acknowledged=true;s.age=0; }
            s.status="等待黑方落子";
        } else if(!s.position.sameBoard(current)) {
            Position next=s.afterWhite.matchReply(current);
            if(next!=null) {
                if(next.king(1)<0) { stop("白王已被吃掉，本局停止。重新开局后可再次开启。");return; }
                s.history.add(s.afterWhite.board.clone());s.history.add(next.board.clone());
                if(s.history.size()>80) s.history.subList(0,40).clear();
                s.position=next;s.afterWhite=null;s.phase=Phase.THINK;s.age=0;s.moved++;
                s.status="黑方已落子，准备计算";return;
            }
            stop("棋盘变化无法对应本局棋步，已停止（可能已重置或有人手动操作）。");return;
        }
        if(s.age>(s.acknowledged?AI_TIMEOUT:MOVE_TIMEOUT))
            stop(s.acknowledged?"等待黑方超过 60 秒，已停止。":"落子未获确认，已停止；不会反复发送落子请求。");
    }
    private Pos boundCore() {
        ItemStack stick=mc.field_71439_g.func_71045_bC();
        if(stick==null||stick.func_77973_b()!=ChessCore.itemchessstick||!stick.func_77942_o()) return null;
        Pos p=new Pos();if(!p.canReadFromNBT(stick.func_77978_p(),0)) return null;
        p.readFromNBT(stick.func_77978_p(),0);return p;
    }
    private boolean selected(Session s) {
        ItemStack stick=mc.field_71439_g.func_71045_bC();Pos relative=new Pos(),absolute=new Pos();
        if(!relative.canReadFromNBT(stick.func_77978_p(),1)||!absolute.canReadFromNBT(stick.func_77978_p(),2)) return false;
        relative.readFromNBT(stick.func_77978_p(),1);absolute.readFromNBT(stick.func_77978_p(),2);
        int f=Position.from(s.move);
        return relative.X==f%8&&relative.Z==f/8&&absolute.X==s.x-3+f%8&&absolute.Y==s.y&&absolute.Z==s.z+3+f/8;
    }
    private boolean wouldAccidentallyCastle(Session s) {
        int from=Position.from(s.move);if(s.position.board[from]!=Position.ROOK) return false;
        ItemStack stick=mc.field_71439_g.func_71045_bC();Pos selected=new Pos();
        if(!selected.canReadFromNBT(stick.func_77978_p(),2)) return false;
        selected.readFromNBT(stick.func_77978_p(),2);
        int x=selected.X-(s.x-3),z=selected.Z-(s.z+3);
        if(x<0||x>7||z<0||z>7||selected.Y!=s.y||s.position.board[x+z*8]!=Position.KING) return false;
        int[] legal=new int[256];int n=s.position.moves(legal);
        for(int i=0;i<n;i++) if(Position.from(legal[i])==x+z*8&&Math.abs(Position.to(legal[i])-Position.from(legal[i]))==2) return true;
        return false;
    }
    private void click(Session s,int square,int y) {
        int x=s.x-3+square%8,z=s.z+3+square/8;
        mc.field_71439_g.field_71174_a.func_147297_a(new C08PacketPlayerBlockPlacement(x,y,z,1,
                mc.field_71439_g.func_71045_bC(),0.5f,1.0f,0.5f));
    }
    private boolean wholeBoardInReach(Session s) {
        for(int x=0;x<8;x+=7) for(int z=0;z<8;z+=7) for(int y=s.y-1;y<=s.y;y++) {
            double dx=s.player.field_70165_t-(s.x-3+x+0.5),dy=s.player.field_70163_u-(y+0.5),dz=s.player.field_70161_v-(s.z+3+z+0.5);
            if(dx*dx+dy*dy+dz*dz>=REACH_SQUARED) return false;
        }
        return true;
    }
    private byte[] readBoard(Session s) {
        byte[] b=new byte[64];
        for(int z=0;z<8;z++) for(int x=0;x<8;x++) {
            int wx=s.x-3+x,wz=s.z+3+z;
            if(!s.world.func_72899_e(wx,s.y,wz)||s.world.func_147439_a(wx,s.y-1,wz)!=ChessCore.BlockChessboard) return null;
            if(s.world.func_147439_a(wx,s.y,wz)==ChessCore.BlcokChess) {
                int meta=s.world.func_72805_g(wx,s.y,wz);if(meta<0||meta>11) return null;
                TileEntity piece=s.world.func_147438_o(wx,s.y,wz);
                if(!(piece instanceof TileEntityChessBase)) return null;
                // Original selection FX reads TileEntity.blockMetadata directly. Prime
                // vanilla's lazy metadata cache, otherwise unrendered pieces have -1
                // and the original packet handler disconnects with a null ChessType.
                if(piece.func_145832_p()!=meta) return null;
                b[x+z*8]=(byte)((meta%6+1)*(meta<6?1:-1));
            } else if(!s.world.func_147437_c(wx,s.y,wz)) return null;
        }
        return b;
    }
}
