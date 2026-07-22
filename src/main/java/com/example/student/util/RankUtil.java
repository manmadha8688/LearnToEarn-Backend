package com.example.student.util;

/**
 * Hunter rank (E → S) from total XP. Thresholds are shared with the frontend
 * ({@code FrontEnd/src/constants/ranks.js} + {@code utils/slRank.js}).
 */
public final class RankUtil {

    public static final long THRESHOLD_D = 1_000;
    public static final long THRESHOLD_C = 3_500;
    public static final long THRESHOLD_B = 8_000;
    public static final long THRESHOLD_A = 16_000;
    public static final long THRESHOLD_S = 30_000;

    private RankUtil() {}

    public static String computeRank(long xp) {
        if (xp >= THRESHOLD_S) return "S";
        if (xp >= THRESHOLD_A) return "A";
        if (xp >= THRESHOLD_B) return "B";
        if (xp >= THRESHOLD_C) return "C";
        if (xp >= THRESHOLD_D) return "D";
        return "E";
    }
}
