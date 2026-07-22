package com.example.student.config;

public class QuizConstants {
    public static final int CONCEPT_TOTAL = 10;
    public static final int CONCEPT_PASS = 8;
    public static final long CONCEPT_RETRY_MINUTES = 10;

    public static final int SUBJECT_TOTAL = 25;
    public static final int SUBJECT_PASS = 19;
    public static final int SUBJECT_TIME_MINUTES = 30;
    public static final long SUBJECT_RETRY_HOURS = 24;

    public static final int ROADMAP_TOTAL = 50;
    public static final int ROADMAP_INTERVIEW_READY = 35;
    public static final int ROADMAP_JOB_READY = 42;
    public static final int ROADMAP_TIME_MINUTES = 90;
    public static final long ROADMAP_RETRY_HOURS = 48;

    /** Skill trial XP: base at pass mark + bonus per extra correct above pass. */
    public static final int CONCEPT_XP_BASE = 50;
    public static final int CONCEPT_XP_PER_POINT = 10;

    /** Gate final test XP (pass 19/25). */
    public static final int SUBJECT_XP_BASE = 150;
    public static final int SUBJECT_XP_PER_POINT = 8;

    /** Path final trial XP (pass 35/50) + one-time Job Ready bump at 42+. */
    public static final int ROADMAP_XP_BASE = 300;
    public static final int ROADMAP_XP_PER_POINT = 5;
    public static final int ROADMAP_JOB_READY_XP_BONUS = 25;

    /** Core aptitude mock (50 Q) — pass 70% (35/50), practice-tier XP. */
    public static final int MOCK_TOTAL = 50;
    public static final int MOCK_PASS = 35;
    public static final int MOCK_XP_BASE = 120;
    public static final int MOCK_XP_PER_POINT = 3;
    public static final long MOCK_RETRY_MINUTES = 15;

    public static final int CONCEPT_DAILY_BONUS_XP = 50;

    /** XP for a passed skill trial (8→50, 9→60, 10→70). Returns 0 below pass. */
    public static int conceptQuizXp(int score) {
        if (score < CONCEPT_PASS) return 0;
        return CONCEPT_XP_BASE + (score - CONCEPT_PASS) * CONCEPT_XP_PER_POINT;
    }

    /** XP for a passed gate test (19→150 … 25→198). Returns 0 below pass. */
    public static int subjectQuizXp(int score) {
        if (score < SUBJECT_PASS) return 0;
        return SUBJECT_XP_BASE + (score - SUBJECT_PASS) * SUBJECT_XP_PER_POINT;
    }

    /** XP for a passed path trial (35→300 … 50→375, +25 at Job Ready). Returns 0 below pass. */
    public static int roadmapQuizXp(int score) {
        if (score < ROADMAP_INTERVIEW_READY) return 0;
        int xp = ROADMAP_XP_BASE + (score - ROADMAP_INTERVIEW_READY) * ROADMAP_XP_PER_POINT;
        if (score >= ROADMAP_JOB_READY) xp += ROADMAP_JOB_READY_XP_BONUS;
        return xp;
    }

    /** XP for a passed core mock (35→120 … 50→165). Returns 0 below pass. */
    public static int mockQuizXp(int score) {
        if (score < MOCK_PASS) return 0;
        return MOCK_XP_BASE + (score - MOCK_PASS) * MOCK_XP_PER_POINT;
    }

    /** Award only the XP delta when a student beats their previous best mock score. */
    public static int mockQuizXpDelta(int newScore, int previousBestScore) {
        return Math.max(0, mockQuizXp(newScore) - mockQuizXp(previousBestScore));
    }

    /** Award only the XP delta when a student beats their previous best gate/path score. */
    public static int quizXpDelta(int newScore, int previousBestScore, String type) {
        int newXp = switch (type) {
            case "SUBJECT" -> subjectQuizXp(newScore);
            case "ROADMAP" -> roadmapQuizXp(newScore);
            default -> 0;
        };
        int oldXp = switch (type) {
            case "SUBJECT" -> subjectQuizXp(previousBestScore);
            case "ROADMAP" -> roadmapQuizXp(previousBestScore);
            default -> 0;
        };
        return Math.max(0, newXp - oldXp);
    }
}
