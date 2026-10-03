package local.foxfoxm3tools.water;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import org.lwjgl.input.Keyboard;

/** Small local walking graph. Movement uses the normal forward/jump key bindings. */
final class Navigator {
    interface Goal { boolean accepts(double x, double feet, double z); }
    private final Minecraft mc;
    private final int ox, by, oz;
    private List<Node> path;
    private int index, ticks;
    private boolean ownsKeys;
    private static final class Node {
        final int x,y,z;
        Node parent;
        Node(int x,int y,int z,Node parent) { this.x=x;this.y=y;this.z=z;this.parent=parent; }
        String key() { return x+","+y+","+z; }
    }
    Navigator(Minecraft mc,int ox,int by,int oz) { this.mc=mc;this.ox=ox;this.by=by;this.oz=oz; }
    double feet() { return mc.field_71439_g.field_70121_D.field_72338_b; }
    boolean move(Goal goal) {
        if (goal.accepts(mc.field_71439_g.field_70165_t,feet(),mc.field_71439_g.field_70161_v)
                && mc.field_71439_g.field_70122_E) { reset();return true; }
        if (path==null || ++ticks>70) {
            // A teleport, jump or freshly removed support can leave the player between
            // integer floor levels. Wait for landing before constructing a new graph.
            if (!mc.field_71439_g.field_70122_E) { reset();return false; }
            path=find(goal);index=0;ticks=0;
        }
        if (path==null) {
            System.err.println("[Water-Maze-Auto] No walking route from "+mc.field_71439_g.field_70165_t+","+feet()+","+mc.field_71439_g.field_70161_v);
            throw new IllegalStateException("找不到安全步行路线，请靠近迷宫后再开启。");
        }
        while (index<path.size()) {
            Node n=path.get(index);
            double dx=n.x+.5-mc.field_71439_g.field_70165_t, dz=n.z+.5-mc.field_71439_g.field_70161_v;
            if (dx*dx+dz*dz<.045 && Math.abs(feet()-n.y)<.15 && mc.field_71439_g.field_70122_E) {
                index++;ticks=0;continue;
            }
            mc.field_71439_g.field_70177_z=(float)(Math.atan2(-dx,dz)*180/Math.PI);
            mc.field_71439_g.field_70125_A=15;
            mc.field_71439_g.func_70031_b(false);
            boolean aligned=dx*dx+dz*dz<.02;
            set(mc.field_71474_y.field_74351_w,!aligned);
            set(mc.field_71474_y.field_74314_A,n.y>feet()+.3 && mc.field_71439_g.field_70122_E);
            ownsKeys=true;return false;
        }
        reset();return false;
    }
    private List<Node> find(Goal goal) {
        int sx=(int)Math.floor(mc.field_71439_g.field_70165_t), sz=(int)Math.floor(mc.field_71439_g.field_70161_v);
        int sy=(int)Math.round(feet());
        if (sx<ox-4||sx>ox+11||sz<oz-4||sz>oz+11) return null;
        Node start=new Node(sx,sy,sz,null);
        ArrayDeque<Node> queue=new ArrayDeque<Node>();queue.add(start);
        Set<String> visited=new HashSet<String>();visited.add(start.key());
        int[] dx={1,-1,0,0},dz={0,0,1,-1};
        int rise=maxRise();
        while (!queue.isEmpty()) {
            Node n=queue.remove();
            if (stand(n.x,n.y,n.z) && goal.accepts(n.x+.5,n.y,n.z+.5)) {
                List<Node> result=new ArrayList<Node>();
                for (Node p=n;p!=null;p=p.parent) result.add(p);
                Collections.reverse(result);return result;
            }
            for (int d=0;d<4;d++) {
                int x=n.x+dx[d],z=n.z+dz[d];
                if (x<ox-4||x>ox+11||z<oz-4||z>oz+11) continue;
                for (int y=n.y+rise;y>=n.y-3;y--) {
                    if (y<by||y>by+5 || !stand(x,y,z)) continue;
                    // A jump must also clear the source column; descending
                    // must pass through the destination column without clipping walls.
                    if (!clear(n.x,Math.min(n.y,y),n.z,Math.max(n.y,y)+1.8)
                            && y>n.y) continue;
                    if (!clear(x,y,z,Math.max(n.y,y)+1.8)) continue;
                    Node next=new Node(x,y,z,n);
                    if (visited.add(next.key())) queue.add(next);
                    break;
                }
            }
        }
        return null;
    }
    private int maxRise() {
        // The original magic trial grants Jump Boost II. Its two-block jump is
        // required to collect a removed upper pipe from the top of its pillars.
        PotionEffect boost=mc.field_71439_g.func_70660_b(Potion.field_76430_j);
        double velocity=.42+(boost==null?0:.1*(boost.func_76458_c()+1)),height=0;
        for (int tick=0;tick<40 && velocity>0;tick++) { height+=velocity;velocity=(velocity-.08)*.98; }
        return Math.max(1,Math.min(3,(int)Math.floor(height)));
    }
    private boolean clear(int x,int y,int z,double top) {
        return mc.field_71441_e.func_72899_e(x,y,z) && mc.field_71441_e.func_72945_a(mc.field_71439_g,
                AxisAlignedBB.func_72330_a(x+.2,y+.01,z+.2,x+.8,top,z+.8)).isEmpty();
    }
    private boolean stand(int x,int y,int z) {
        return clear(x,y,z,y+1.8) && !mc.field_71441_e.func_72945_a(mc.field_71439_g,
                AxisAlignedBB.func_72330_a(x+.2,y-.08,z+.2,x+.8,y-.01,z+.8)).isEmpty();
    }
    void reset() {
        path=null;index=0;ticks=0;
        if (ownsKeys) {
            restore(mc.field_71474_y.field_74351_w);restore(mc.field_71474_y.field_74314_A);ownsKeys=false;
        }
    }
    static boolean physicallyDown(KeyBinding key) {
        int code=key.func_151463_i();
        return code>0 ? Keyboard.isKeyDown(code) : code<0 && code+100>=0
                && code+100<org.lwjgl.input.Mouse.getButtonCount() && org.lwjgl.input.Mouse.isButtonDown(code+100);
    }
    private void restore(KeyBinding key) { set(key,physicallyDown(key)); }
    private void set(KeyBinding key,boolean down) { KeyBinding.func_74510_a(key.func_151463_i(),down); }
}
