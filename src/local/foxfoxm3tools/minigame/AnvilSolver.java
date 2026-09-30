package local.foxfoxm3tools.minigame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Least worst-case workpiece wear, then fewest hits, using the actual clamped bar. */
public final class AnvilSolver {
    private static final int[] DELTA = {-12, -9, -6, -3, 2, 4, 6, 8};
    private static final int[] WEAR = {6, 4, 3, 2, 2, 3, 4, 6};
    private static final int INF = 100000000;
    private AnvilSolver() {}
    public static int next(int pos, int action) { return Math.max(0, Math.min(120, pos + DELTA[action])); }
    public static int wear(int action) { return WEAR[action]; }
    private static int cost(int action) { return WEAR[action] * 100 + 1; }
    private static int consume(int mask, int action, int[] required) {
        for (int i = 0; i < 3; i++) {
            if ((mask & (1 << i)) == 0 && required[i] == action) return mask | (1 << i);
        }
        return mask;
    }

    public static int[] solve(int start, int target, int[] required) {
        if (start < 0 || start > 120 || target < 0 || target > 120 || required.length != 3)
            throw new IllegalArgumentException("Invalid anvil state");
        for (int a : required) if (a < 0 || a > 7) throw new IllegalArgumentException("Invalid action");
        // A suffix of exactly seven hits guarantees the three requested actions
        // (including duplicates) are present in ManaMetal's last-seven window.
        int[][][] suffix = new int[8][121][8];
        int[][][] choice = new int[8][121][8];
        for (int n = 0; n <= 7; n++) for (int p = 0; p <= 120; p++) Arrays.fill(suffix[n][p], INF);
        suffix[0][target][7] = 0;
        for (int n = 1; n <= 7; n++) for (int p = 0; p <= 120; p++) for (int mask = 0; mask < 8; mask++) {
            for (int a = 0; a < 8; a++) {
                int c = cost(a) + suffix[n - 1][next(p, a)][consume(mask, a, required)];
                if (c < suffix[n][p][mask]) { suffix[n][p][mask] = c; choice[n][p][mask] = a; }
            }
        }
        int[] distance = new int[121], previous = new int[121], action = new int[121];
        boolean[] seen = new boolean[121];
        Arrays.fill(distance, INF); distance[start] = 0;
        for (int n = 0; n <= 120; n++) {
            int p = -1;
            for (int k = 0; k <= 120; k++) if (!seen[k] && (p < 0 || distance[k] < distance[p])) p = k;
            if (p < 0 || distance[p] == INF) break;
            seen[p] = true;
            for (int a = 0; a < 8; a++) {
                int q = next(p, a), c = distance[p] + cost(a);
                if (c < distance[q]) { distance[q] = c; previous[q] = p; action[q] = a; }
            }
        }
        int join = start;
        for (int p = 0; p <= 120; p++)
            if (distance[p] + suffix[7][p][0] < distance[join] + suffix[7][join][0]) join = p;
        if (distance[join] + suffix[7][join][0] >= INF) return new int[0];
        List<Integer> plan = new ArrayList<Integer>();
        for (int p = join; p != start; p = previous[p]) plan.add(action[p]);
        Collections.reverse(plan);
        int p = join, mask = 0;
        for (int n = 7; n > 0; n--) {
            int a = choice[n][p][mask]; plan.add(a);
            p = next(p, a); mask = consume(mask, a, required);
        }
        int[] result = new int[plan.size()];
        for (int i = 0; i < result.length; i++) result[i] = plan.get(i);
        return result;
    }
}
