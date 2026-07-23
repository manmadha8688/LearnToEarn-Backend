package com.example.student.util;

/**
 * XP → Level mapping for the escalating level curve.
 *
 * <p>Level is driven purely by lifetime XP (rank is a separate, category-gated signal).
 * The per-level XP cost starts at 100 and grows by 15 each level, so early levels come
 * fast and later ones take progressively longer:
 * <pre>
 *   L1→L2 = 100, L2→L3 = 115, L3→L4 = 130, … (+15 each step)
 * </pre>
 *
 * Cumulative XP required to reach level {@code L}:
 * <pre>
 *   cumulative(L) = 100*(L-1) + 15*(L-1)*(L-2)/2
 * </pre>
 * (cumulative(1) = 0, cumulative(2) = 100, cumulative(3) = 215, cumulative(4) = 345, …)
 */
public final class LevelUtil {

    private LevelUtil() {}

    /** Total lifetime XP needed to have reached {@code level} (level 1 = 0 XP). */
    public static long cumulativeXpForLevel(int level) {
        if (level <= 1) return 0L;
        long n = level - 1L;
        return 100L * n + 15L * n * (n - 1L) / 2L;
    }

    /** Highest level whose cumulative XP requirement is satisfied by {@code xp} (min 1). */
    public static int levelForXp(long xp) {
        if (xp <= 0) return 1;
        int level = 1;
        while (cumulativeXpForLevel(level + 1) <= xp) {
            level++;
        }
        return level;
    }
}
