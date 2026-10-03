package local.foxfoxm3tools.chess;

import java.util.Arrays;

/** The 8.0.7 board: a1 is (0,0), white advances toward increasing Z.
 * Unlike tournament chess the game's terminal condition is capture of a king. */
public final class Position {
    public static final int PAWN=1, KNIGHT=2, BISHOP=3, ROOK=4, KING=5, QUEEN=6;
    public final byte[] board;
    public final int side, castles, ep;
    private static final int[][] DIAGONAL={{1,1},{1,-1},{-1,1},{-1,-1}};
    private static final int[][] STRAIGHT={{1,0},{-1,0},{0,1},{0,-1}};
    private static final int[][] KNIGHTS={{1,2},{2,1},{2,-1},{1,-2},{-1,-2},{-2,-1},{-2,1},{-1,2}};

    public Position(byte[] board, int side, int castles, int ep) {
        if (board.length != 64) throw new IllegalArgumentException("64 squares required");
        this.board=board.clone(); this.side=side; this.castles=castles; this.ep=ep;
    }
    public static Position initial() {
        byte[] b=new byte[64]; int[] back={ROOK,KNIGHT,BISHOP,QUEEN,KING,BISHOP,KNIGHT,ROOK};
        for(int x=0;x<8;x++) { b[x]=(byte)back[x]; b[8+x]=PAWN; b[48+x]=-PAWN; b[56+x]=(byte)-back[x]; }
        return new Position(b,1,15,-1);
    }
    public static int move(int from,int to) { return from | to<<6; }
    public static int from(int m) { return m&63; }
    public static int to(int m) { return m>>6&63; }
    public static String square(int s) { return ""+(char)('a'+s%8)+(s/8+1); }
    public static String name(int m) { return square(from(m))+" → "+square(to(m)); }
    public boolean sameBoard(byte[] b) { return Arrays.equals(board,b); }
    public int king(int color) { for(int s=0;s<64;s++) if(board[s]==color*KING) return s; return -1; }

    /** Pseudo-legal moves deliberately match TileEntityChessAI, including king captures. */
    public int moves(int[] out) {
        int n=0;
        for(int s=0;s<64;s++) {
            int p=board[s]*side, x=s%8, z=s/8;
            if(p<=0) continue;
            if(p==PAWN) {
                int zz=z+side;
                if(zz<0||zz>7) continue;
                int t=x+zz*8;
                if(board[t]==0) {
                    out[n++]=move(s,t);
                    if(z==(side==1?1:6)&&board[t+side*8]==0) out[n++]=move(s,t+side*8);
                }
                for(int dx=-1;dx<=1;dx+=2) {
                    int xx=x+dx; if(xx<0||xx>7) continue;
                    t=xx+zz*8;
                    if(board[t]*side<0||(t==ep&&board[t-side*8]==-side*PAWN)) out[n++]=move(s,t);
                }
            } else if(p==KNIGHT) {
                for(int[] d:KNIGHTS) {
                    int xx=x+d[0],zz=z+d[1];
                    if(xx>=0&&xx<8&&zz>=0&&zz<8&&board[xx+zz*8]*side<=0) out[n++]=move(s,xx+zz*8);
                }
            } else if(p==KING) {
                for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
                    if(dx==0&&dz==0) continue;
                    int xx=x+dx,zz=z+dz;
                    if(xx>=0&&xx<8&&zz>=0&&zz<8&&board[xx+zz*8]*side<=0) out[n++]=move(s,xx+zz*8);
                }
                int home=side==1?0:56, shift=side==1?0:2;
                if(s==home+4&&!attacked(s,-side)) {
                    if((castles&(1<<shift))!=0&&board[home+7]==side*ROOK&&board[home+5]==0&&board[home+6]==0
                            &&!attacked(home+5,-side)&&!attacked(home+6,-side)) out[n++]=move(s,home+6);
                    if((castles&(2<<shift))!=0&&board[home]==side*ROOK&&board[home+1]==0&&board[home+2]==0&&board[home+3]==0
                            &&!attacked(home+3,-side)&&!attacked(home+2,-side)) out[n++]=move(s,home+2);
                }
            } else {
                if(p==BISHOP||p==QUEEN) n=slides(out,n,s,DIAGONAL);
                if(p==ROOK||p==QUEEN) n=slides(out,n,s,STRAIGHT);
            }
        }
        return n;
    }
    private int slides(int[] out,int n,int s,int[][] directions) {
        for(int[] d:directions) for(int x=s%8+d[0],z=s/8+d[1];x>=0&&x<8&&z>=0&&z<8;x+=d[0],z+=d[1]) {
            int t=x+z*8; if(board[t]*side>0) break;
            out[n++]=move(s,t); if(board[t]!=0) break;
        }
        return n;
    }
    public boolean attacked(int target,int color) {
        if(target<0) return true;
        int x=target%8,z=target/8;
        for(int s=0;s<64;s++) {
            int p=board[s]*color; if(p<=0) continue;
            int dx=x-s%8,dz=z-s/8, ax=Math.abs(dx),az=Math.abs(dz);
            if(p==PAWN) { if(ax==1&&dz==color) return true; continue; }
            if(p==KNIGHT) { if(ax*az==2) return true; continue; }
            if(p==KING) { if(Math.max(ax,az)==1) return true; continue; }
            boolean straight=(dx==0)!=(dz==0), diagonal=ax==az&&ax!=0;
            if(!((straight&&(p==ROOK||p==QUEEN))||(diagonal&&(p==BISHOP||p==QUEEN)))) continue;
            int stepX=Integer.signum(dx),stepZ=Integer.signum(dz),xx=s%8+stepX,zz=s/8+stepZ;
            while(xx!=x||zz!=z) { if(board[xx+zz*8]!=0) break; xx+=stepX; zz+=stepZ; }
            if(xx==x&&zz==z) return true;
        }
        return false;
    }
    public Position play(int m) {
        int f=from(m),t=to(m),p=board[f],rights=castles,newEp=-1;
        byte[] b=board.clone(); b[f]=0; b[t]=(byte)p;
        if(Math.abs(p)==PAWN) {
            if(t==ep&&board[t]==0&&f%8!=t%8) b[t-side*8]=0;
            if(Math.abs(t-f)==16) newEp=(t+f)/2;
            if(t/8==0||t/8==7) b[t]=(byte)(side*QUEEN);
        }
        if(Math.abs(p)==KING) {
            rights &= side==1?12:3;
            if(Math.abs(t-f)==2) { int r=t>f?f+3:f-4; b[r]=0; b[t>f?t-1:t+1]=(byte)(side*ROOK); }
        }
        // Rights can only be lost, including a rook captured on its home square.
        if(f==7||t==7) rights&=~1; if(f==0||t==0) rights&=~2;
        if(f==63||t==63) rights&=~4; if(f==56||t==56) rights&=~8;
        return new Position(b,-side,rights,newEp);
    }
    public boolean safeAfter(Position next) {
        return next.king(-side)<0 || !next.attacked(next.king(side),-side);
    }
    public Position matchReply(byte[] observed) {
        int[] moves=new int[256]; int n=moves(moves);
        for(int i=0;i<n;i++) { Position p=play(moves[i]); if(p.sameBoard(observed)) return p; }
        return null;
    }
}
