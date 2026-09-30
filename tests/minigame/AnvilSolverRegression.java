import java.util.*;
import local.foxfoxm3tools.minigame.AnvilSolver;

public final class AnvilSolverRegression {
    public static void main(String[] args) {
        Random random = new Random(91237);
        int cases = 0;
        for (int i = 0; i < 3000; i++) {
            int start = i % 3 == 0 ? 0 : i % 3 == 1 ? 120 : random.nextInt(121);
            int target = 40 + random.nextInt(70);
            int[] need = {random.nextInt(8), random.nextInt(8), random.nextInt(8)};
            if (i < 512) need = new int[]{i % 8, i / 8 % 8, i / 64};
            int[] plan = AnvilSolver.solve(start, target, need);
            if (plan.length < 7 || plan.length > 40) throw new AssertionError("plan length");
            int pos = start;
            int[] delta = {-12,-9,-6,-3,2,4,6,8};
            for (int action : plan) pos = Math.max(0, Math.min(120, pos + delta[action]));
            if (pos != target) throw new AssertionError("wrong target");
            int[] counts = new int[8];
            for (int n = plan.length - 7; n < plan.length; n++) counts[plan[n]]++;
            for (int action : need) if (--counts[action] < 0) throw new AssertionError("missing repeated action");
            cases++;
        }
        System.out.println("PASS: " + cases + " anvil plans, all 512 requirement combinations, repeated actions and clamped boundaries");
    }
}
