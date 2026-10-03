package local.foxfoxm3tools.water;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Block;
import project.studio.manametalmod.watergame.*;

/** Replays only the pure generator, using the seed already synchronized to clients.
 * Never calls generate/place/giveItems or writes to a world. */
public final class Blueprint {
    public final List<Cell> cells = new ArrayList<Cell>();
    public final WaterGamePuzzleGenerator.Result puzzle;

    public static final class Cell {
        public final int x, y, z, meta;
        public final WaterGameType type;
        public final boolean editable;
        Cell(int x, int y, int z, WaterGameType type, WaterDirection direction, boolean editable) {
            this.x=x; this.y=y; this.z=z; this.type=type; this.meta=meta(direction); this.editable=editable;
        }
        public Block block() { return Blueprint.block(type); }
        public boolean matches(WaterGameType actual, int direction) {
            if (actual != type) return false;
            if (type==WaterGameType.X || type==WaterGameType.end) return true;
            if (type==WaterGameType.I) return direction%2==meta%2;
            return direction==meta;
        }
    }

    public Blueprint(long seed) throws Exception {
        Method create=WaterGamePuzzleGenerator.class.getDeclaredMethod("createPuzzle", Random.class);
        Method fallback=WaterGamePuzzleGenerator.class.getDeclaredMethod("createFallbackPuzzle");
        create.setAccessible(true); fallback.setAccessible(true);
        Random random=new Random(seed);
        WaterGamePuzzleGenerator.Result result=null;
        for (int tries=0; tries<160; tries++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            result=(WaterGamePuzzleGenerator.Result)create.invoke(null,random);
            if (result!=null && result.verify()) break;
            result=null;
        }
        if (result==null) result=(WaterGamePuzzleGenerator.Result)fallback.invoke(null);
        puzzle=result;
        for (int i=0; i<result.pathLength; i++) {
            int x=result.pathX[i], z=result.pathZ[i];
            cells.add(new Cell(x,result.highLayer[x][z]?3:1,z,result.type[x][z],result.direction[x][z],
                    result.missing[x][z] || result.movable[x][z]));
        }
    }
    public static int meta(WaterDirection direction) {
        switch (direction) {
            case Dowm: return 0;
            case Right: return 1;
            case Up: return 2;
            case Left: return 3;
            default: throw new IllegalArgumentException("direction");
        }
    }
    public static Block block(WaterGameType type) {
        switch (type) {
            case soure: return WaterGameCore.soure;
            case end: return WaterGameCore.end;
            case I: return WaterGameCore.I;
            case L: return WaterGameCore.L;
            case T: return WaterGameCore.T;
            case X: return WaterGameCore.X;
            case WaterDown: return WaterGameCore.WaterDown;
            default: throw new IllegalArgumentException("Unsupported answer piece: "+type);
        }
    }
}
