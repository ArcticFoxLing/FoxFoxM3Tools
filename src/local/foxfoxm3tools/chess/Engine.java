package local.foxfoxm3tools.chess;

import java.util.*;
import java.util.concurrent.CancellationException;

/** Bounded iterative deepening. Works exclusively on detached board snapshots. */
public final class Engine {
    public static final int WIN=100000;
    private static final int[] VALUE={0,100,320,335,500,20000,950};
    private final int[][] moves=new int[64][256], order=new int[64][256];
    private final Map<Long,Entry> table=new HashMap<Long,Entry>();
    private long deadline,nodes;
    private int rootMove;
    private List<byte[]> history;
    private final long[] path=new long[64];
    private static final class Entry { long key; int depth,score,move,flag; }
    public static final class Result {
        public final int move,depth,score; public final long nodes;
        Result(int m,int d,int s,long n) { move=m;depth=d;score=s;nodes=n; }
    }
    private static final class TimeUp extends RuntimeException {
        @Override public synchronized Throwable fillInStackTrace() { return this; }
    }
    public Result search(Position p,int milliseconds,List<byte[]> previous) {
        deadline=System.nanoTime()+milliseconds*1000000L; nodes=0; table.clear();
        history=previous==null?Collections.<byte[]>emptyList():previous;
        int[] candidates=new int[256]; int n=p.moves(candidates),best=-1,score=0,depth=0;
        for(int i=0;i<n;i++) if(p.safeAfter(p.play(candidates[i]))) { best=candidates[i]; break; }
        if(best<0&&n>0) best=candidates[0];
        if(best<0) return new Result(-1,0,-WIN,0);
        for(int d=1;d<=9;d++) {
            try { rootMove=best; int s=negamax(p,d,-WIN-1,WIN+1,0,0); best=rootMove;score=s;depth=d; }
            catch(TimeUp stop) { break; }
            if(Math.abs(score)>WIN-100) break;
        }
        return new Result(best,depth,score,nodes);
    }
    private int negamax(Position p,int depth,int alpha,int beta,int ply,int qdepth) {
        nodes++;
        if((nodes&127)==0) {
            if(Thread.currentThread().isInterrupted()) throw new CancellationException();
            if(System.nanoTime()>=deadline) throw new TimeUp();
        }
        if(p.king(p.side)<0) return -WIN+ply;
        if(p.king(-p.side)<0) return WIN-ply;
        if(ply>=60) return evaluate(p);
        long key=hash(p);
        if(ply>0) for(int j=ply-2;j>=0;j-=2) if(path[j]==key) return 0;
        path[ply]=key;
        int oldAlpha=alpha, preferred=-1;
        Entry cached=depth>0?table.get(key):null;
        if(cached!=null) {
            preferred=cached.move;
            if(ply>0&&cached.depth>=depth&&Math.abs(cached.score)<WIN-100
                    &&(cached.flag==0||cached.flag==1&&cached.score>=beta||cached.flag==2&&cached.score<=alpha)) return cached.score;
        }
        boolean check=p.attacked(p.king(p.side),-p.side), quiet=depth<=0;
        if(quiet&&!check) {
            int stand=evaluate(p); if(stand>=beta) return stand; if(stand>alpha) alpha=stand;
        }
        if(quiet&&qdepth>=8) return evaluate(p);
        int n=p.moves(moves[ply]),legal=0,best=-1;
        for(int i=0;i<n;i++) {
            int m=moves[ply][i],f=Position.from(m),t=Position.to(m);
            int capture=Math.abs(p.board[t]);
            order[ply][i]=(m==preferred?1000000:0)+(capture>0?VALUE[capture]*16-VALUE[Math.abs(p.board[f])]:0)
                    +(Math.abs(p.board[f])==Position.PAWN&&(t/8==0||t/8==7)?15000:0);
        }
        for(int i=0;i<n;i++) {
            int top=i; for(int j=i+1;j<n;j++) if(order[ply][j]>order[ply][top]) top=j;
            int m=moves[ply][top]; moves[ply][top]=moves[ply][i];moves[ply][i]=m;
            int priority=order[ply][top];order[ply][top]=order[ply][i];order[ply][i]=priority;
            int f=Position.from(m),t=Position.to(m);
            boolean promotion=Math.abs(p.board[f])==Position.PAWN&&(t/8==0||t/8==7);
            if(quiet&&!check&&p.board[t]==0&&t!=p.ep&&!promotion) continue;
            Position next=p.play(m);
            if(!p.safeAfter(next)) continue;
            legal++;
            int score=-negamax(next,depth-1,-beta,-alpha,ply+1,quiet?qdepth+1:0);
            if(ply==0&&Math.abs(score)<WIN-100) {
                for(byte[] h:history) if(Arrays.equals(h,next.board)) score-=35;
            }
            if(score>alpha) { alpha=score;best=m; if(ply==0) rootMove=m; }
            if(alpha>=beta) break;
        }
        if(legal==0&&(!quiet||check)) return check?-WIN+ply:0;
        if(depth>0&&table.size()<150000) {
            Entry e=new Entry();e.key=key;e.depth=depth;e.score=alpha;e.move=best<0?preferred:best;
            e.flag=alpha<=oldAlpha?2:alpha>=beta?1:0;table.put(key,e);
        }
        return alpha;
    }
    private int evaluate(Position p) {
        int score=0,nonPawn=0;
        for(byte v:p.board) if(Math.abs(v)>1&&Math.abs(v)!=Position.KING) nonPawn+=VALUE[Math.abs(v)];
        for(int s=0;s<64;s++) {
            int v=p.board[s]; if(v==0) continue;
            int sign=v>0?1:-1,type=Math.abs(v),x=s%8,z=sign==1?s/8:7-s/8;
            int center=7-(Math.abs(2*x-7)+Math.abs(2*z-7))/2;
            int value=VALUE[type];
            if(type==Position.PAWN) value+=z*z*4+center*3;
            if(type==Position.KNIGHT) value+=center*10;
            if(type==Position.BISHOP) value+=center*6;
            if(type==Position.ROOK) value+=z*2;
            if(type==Position.QUEEN) value+=center*2;
            if(type==Position.KING) value+=nonPawn<1800?center*10:-z*12-center*3;
            score+=sign*value;
        }
        // In won endings drive the opponent king to an edge and bring our king closer.
        int wk=p.king(1),bk=p.king(-1);
        if(nonPawn<2200&&Math.abs(score)>200&&wk>=0&&bk>=0) {
            int winner=score>0?1:-1,loser=winner==1?bk:wk;
            int edge=Math.min(Math.min(loser%8,7-loser%8),Math.min(loser/8,7-loser/8));
            int distance=Math.abs(wk%8-bk%8)+Math.abs(wk/8-bk/8);
            score+=winner*(60-edge*20+(14-distance)*5);
        }
        return score*p.side;
    }
    private static long hash(Position p) {
        long h=1469598103934665603L;
        for(byte b:p.board) h=(h^(b+7))*1099511628211L;
        return ((h^p.castles)*1099511628211L^p.ep)*31+p.side;
    }
}
