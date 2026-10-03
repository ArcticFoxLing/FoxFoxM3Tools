package local.foxfoxm3tools.chess;

import java.util.*;

public final class EngineCheck {
    private static int checks;
    private static void check(boolean ok,String name) { checks++;if(!ok) throw new AssertionError(name); }
    private static long perft(Position p,int depth) {
        if(depth==0) return 1;
        int[] list=new int[256];int n=p.moves(list);long count=0;
        for(int i=0;i<n;i++) { Position next=p.play(list[i]);if(p.safeAfter(next)) count+=perft(next,depth-1); }
        return count;
    }
    private static boolean has(Position p,int f,int t) {
        int[] list=new int[256];int n=p.moves(list);for(int i=0;i<n;i++) if(list[i]==Position.move(f,t)) return true;return false;
    }
    public static void main(String[] args) throws Exception {
        Position p=Position.initial();
        check(perft(p,1)==20,"initial depth 1");
        check(perft(p,2)==400,"initial depth 2");
        check(perft(p,3)==8902,"initial depth 3");
        check(perft(p,4)==197281,"initial depth 4");
        byte[] b=new byte[64];b[4]=5;b[0]=4;b[7]=4;b[60]=-5;
        p=new Position(b,1,3,-1);
        check(has(p,4,6)&&has(p,4,2),"both castles available");
        Position q=p.play(Position.move(4,6));
        check(q.board[6]==5&&q.board[5]==4&&q.board[7]==0&&q.castles==0,"castle rook and rights");
        b[61]=-4;check(!has(new Position(b,1,3,-1),4,6),"cannot castle across attacked square");
        b=new byte[64];b[4]=5;b[60]=-5;b[36]=1;b[51]=-1;
        p=new Position(b,-1,0,-1).play(Position.move(51,35));
        check(p.ep==43&&has(p,36,43),"en passant offered after double step");
        q=p.play(Position.move(36,43));check(q.board[35]==0&&q.board[43]==1&&q.ep==-1,"en passant capture");
        check(!has(p.play(Position.move(4,3)).play(Position.move(60,59)),36,43),"en passant expires");
        b=new byte[64];b[4]=5;b[60]=-5;b[48]=1;
        p=new Position(b,1,0,-1);q=p.play(Position.move(48,56));check(q.board[56]==6,"automatic queen promotion");
        b=new byte[64];b[4]=5;b[63]=-5;b[7]=4;
        p=new Position(b,1,0,-1);Engine.Result r=new Engine().search(p,500,null);
        check(r.move==Position.move(7,63),"capture exposed king immediately");
        check(r.score>Engine.WIN-100,"king capture evaluated as terminal");
        b=new byte[64];b[4]=5;b[60]=-5;b[12]=4;b[52]=-4;
        p=new Position(b,1,0,-1);r=new Engine().search(p,300,null);
        check(p.safeAfter(p.play(r.move)),"do not expose own king");
        Random rand=new Random(20261003);p=Position.initial();
        for(int i=0;i<300;i++) {
            int[] list=new int[256];int n=p.moves(list);if(n==0||p.king(1)<0||p.king(-1)<0) p=Position.initial();
            n=p.moves(list);int m=list[rand.nextInt(n)];q=p.play(m);
            check(p.matchReply(q.board)!=null,"reconstruct opponent reply "+i);
            p=q;
        }
        p=Position.initial();long started=System.nanoTime();r=new Engine().search(p,200,null);
        check((System.nanoTime()-started)/1000000L<1800,"search deadline");
        check(r.depth>=2&&has(p,Position.from(r.move),Position.to(r.move)),"completed iterative search");
        Thread.currentThread().interrupt();
        try { new Engine().search(p,3000,null);throw new AssertionError("cancellation ignored"); }
        catch(java.util.concurrent.CancellationException expected) { checks++; }
        finally { Thread.interrupted(); }
        System.out.println("PASS: "+checks+" checks; perft 20/400/8902/197281; castle, en passant, promotion, king capture, reply reconstruction, deadlines and cancellation");
        System.out.println("Initial search: "+Position.name(r.move)+", depth "+r.depth+", nodes "+r.nodes);
    }
}
