package com.example.student.service;

import com.example.student.dto.ProgressSummaryDTO;
import com.example.student.config.QuizConstants;
import com.example.student.exception.ResourceNotFoundException;
import com.example.student.model.Concept;
import com.example.student.model.QuizAttempt;
import com.example.student.model.Roadmap;
import com.example.student.model.Subject;
import com.example.student.model.User;
import com.example.student.model.UserConceptProgress;
import com.example.student.repository.ConceptRepository;
import com.example.student.repository.QuizAttemptRepository;
import com.example.student.repository.SubjectRepository;
import com.example.student.repository.UserConceptProgressRepository;
import com.example.student.repository.UserRepository;
import com.example.student.repository.UserSubjectBadgeRepository;
import com.example.student.repository.UserRoadmapBadgeRepository;
import com.example.student.repository.RoadmapRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProgressService {

    private final UserConceptProgressRepository progressRepository;
    private final ConceptRepository conceptRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final UserSubjectBadgeRepository badgeRepository;
    private final UserRoadmapBadgeRepository roadmapBadgeRepository;
    private final RoadmapRepository roadmapRepository;
    private final CacheService cacheService;

    public ProgressService(UserConceptProgressRepository progressRepository,
                           ConceptRepository conceptRepository,
                           SubjectRepository subjectRepository,
                           UserRepository userRepository,
                           QuizAttemptRepository quizAttemptRepository,
                           UserSubjectBadgeRepository badgeRepository,
                           UserRoadmapBadgeRepository roadmapBadgeRepository,
                           RoadmapRepository roadmapRepository,
                           CacheService cacheService) {
        this.progressRepository = progressRepository;
        this.conceptRepository = conceptRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.badgeRepository = badgeRepository;
        this.roadmapBadgeRepository = roadmapBadgeRepository;
        this.roadmapRepository = roadmapRepository;
        this.cacheService = cacheService;
    }

    // All quest/streak/daily-bonus date logic uses IST so it matches the Indian-student
    // audience regardless of the server timezone (Render runs in UTC).
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static String computeRank(long xp) {
        return com.example.student.util.RankUtil.computeRank(xp);
    }

    /**
     * Marks a concept complete and awards XP.
     * @param quizScore the raw quiz score (e.g. 8 for 8/10). Pass 0 for non-quiz completions.
     * @return map with xpEarned and dailyBonusEarned keys
     */
    @Transactional
    public Map<String, Object> completeConcept(String conceptId, String userId, int quizScore) {
        if (progressRepository.existsByUserIdAndConceptId(userId, conceptId)) {
            return Map.of("message", "Already completed", "conceptId", conceptId,
                    "xpEarned", 0, "dailyBonusEarned", false);
        }
        Concept concept = conceptRepository.findById(conceptId)
                .orElseThrow(() -> new ResourceNotFoundException("Concept not found"));

        UserConceptProgress progress = new UserConceptProgress();
        progress.setUserId(userId);
        progress.setConceptId(conceptId);
        progress.setSubjectId(concept.getSubjectId());
        progress.setSubjectTitle(concept.getSubjectTitle());
        progress.setSubjectIcon(concept.getSubjectIcon());
        progress.setCompletedAt(LocalDateTime.now(IST));
        progressRepository.save(progress);

        // XP: 50 base at 8/10 pass, +10 per extra correct (9→60, 10→70)
        int conceptXp = QuizConstants.conceptQuizXp(quizScore);

        // Daily bonus: +50 XP if this is the FIRST concept passed today.
        // Uses QuizAttempt.takenAt (reliable timestamp set at submission time)
        // rather than completedAt (which is less reliable for this check).
        LocalDateTime startOfToday = LocalDate.now(IST).atStartOfDay();
        boolean isFirstToday = !quizAttemptRepository
                .existsByUserIdAndTypeAndPassedTrueAndTakenAtAfterAndRefIdNot(
                        userId, "CONCEPT", startOfToday, conceptId);
        int dailyBonus = isFirstToday ? QuizConstants.CONCEPT_DAILY_BONUS_XP : 0;
        int totalXp = conceptXp + dailyBonus;

        // Update User document
        userRepository.findById(userId).ifPresent(user -> {
            long newXp = user.getXp() + totalXp;
            user.setXp(newXp);
            user.setLevel(Math.max(1, (int)(newXp / 200)));
            user.setRank(computeRank(newXp));
            userRepository.save(user);
        });

        cacheService.evict("progress", "summary:" + userId);
        cacheService.evict("hunterStats", userId);
        cacheService.evict("dashboardBootstrap", userId);
        cacheService.evict("quests", userId + ":" + LocalDate.now(IST));
        cacheService.evictAll("quizStatus");

        return Map.of("message", "Completed", "conceptId", conceptId,
                "xpEarned", totalXp, "dailyBonusEarned", isFirstToday);
    }

    /** Backward-compat overload for non-quiz completions (admin etc.) */
    public Map<String, Object> completeConcept(String conceptId, String userId) {
        return completeConcept(conceptId, userId, 0);
    }

    /**
     * Apply an XP delta to an in-memory User (xp/level/rank), WITHOUT persisting — the caller
     * saves. Floors XP at 0. Used when a caller already holds the User and does its own save
     * (e.g. profile-completion awards batched into a single profile save).
     */
    public int applyXp(User user, int amount) {
        if (user == null || amount == 0) return 0;
        long newXp = Math.max(0, user.getXp() + amount);
        user.setXp(newXp);
        user.setLevel(Math.max(1, (int)(newXp / 200)));
        user.setRank(computeRank(newXp));
        return amount;
    }

    /** Award XP directly (used for subject/roadmap quiz passes) */
    public int awardXp(String userId, int amount) {
        if (amount <= 0) return 0;
        userRepository.findById(userId).ifPresent(user -> {
            long newXp = user.getXp() + amount;
            user.setXp(newXp);
            user.setLevel(Math.max(1, (int)(newXp / 200)));
            user.setRank(computeRank(newXp));
            userRepository.save(user);
        });
        cacheService.evict("progress", "summary:" + userId);
        cacheService.evict("dashboardBootstrap", userId);
        return amount;
    }

    /** Remove XP (used when uncompleting a concept). Never drops below zero. */
    public int deductXp(String userId, int amount) {
        if (amount <= 0) return 0;
        userRepository.findById(userId).ifPresent(user -> {
            long newXp = Math.max(0, user.getXp() - amount);
            user.setXp(newXp);
            user.setLevel(Math.max(1, (int)(newXp / 200)));
            user.setRank(computeRank(newXp));
            userRepository.save(user);
        });
        cacheService.evict("progress", "summary:" + userId);
        cacheService.evict("dashboardBootstrap", userId);
        return amount;
    }

    /**
     * Fully resets a concept: removes the progress record, reverses the XP it awarded,
     * and clears its quiz attempts so status no longer reports "passed" (and the retry
     * cooldown is lifted). Previously this only deleted the progress row, which left XP
     * inflated and the concept still showing as cleared via a lingering passed attempt.
     */
    @Transactional
    public Map<String, Object> uncompleteConcept(String conceptId, String userId) {
        progressRepository.deleteByUserIdAndConceptId(userId, conceptId);

        List<QuizAttempt> attempts = quizAttemptRepository
                .findByUserIdAndTypeAndRefId(userId, "CONCEPT", conceptId);
        int refund = attempts.stream()
                .filter(QuizAttempt::isPassed)
                .mapToInt(QuizAttempt::getXpEarned)
                .sum();
        if (!attempts.isEmpty()) quizAttemptRepository.deleteAll(attempts);
        if (refund > 0) deductXp(userId, refund);

        cacheService.evict("progress", "summary:" + userId);
        cacheService.evict("hunterStats", userId); // conceptsPassed count changed
        cacheService.evict("dashboardBootstrap", userId);
        cacheService.evictAll("quizStatus");
        return Map.of("message", "Unmarked", "xpRemoved", refund);
    }

    public ProgressSummaryDTO getProgressSummary(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User required");
        }
        String userId = user.getId();
        final User xpSource = user;
        return cacheService.get("progress", "summary:" + userId, () -> buildProgressSummary(userId, xpSource));
    }

    private ProgressSummaryDTO buildProgressSummary(String userId, User xpSource) {
        // Single progress fetch — reused for streak, counts, and per-subject breakdown
        List<UserConceptProgress> allProgress = progressRepository.findByUserId(userId);
        long completedConcepts = allProgress.size();
        long totalConcepts = cacheService.getLong("concepts", "total", conceptRepository::count);
        double percentage = totalConcepts > 0
                ? Math.round((completedConcepts * 100.0 / totalConcepts) * 10) / 10.0
                : 0;

        // ── Streak ──────────────────────────────────────────────────────────
        // Consecutive IST days with ≥1 concept completed. If today isn't done yet,
        // count backward from yesterday so the streak stays visible until midnight.
        Set<LocalDate> completionDates = allProgress.stream()
                .filter(p -> p.getCompletedAt() != null)
                .map(p -> p.getCompletedAt().toLocalDate())
                .collect(Collectors.toSet());

        int streak = 0;
        LocalDate today = LocalDate.now(IST);
        LocalDate date = completionDates.contains(today) ? today : today.minusDays(1);
        while (completionDates.contains(date)) { streak++; date = date.minusDays(1); }

        // ── XP / Level / Rank — prefer auth principal on cache miss (skips a User read) ─
        User user = xpSource != null && userId.equals(xpSource.getId())
                ? xpSource
                : userRepository.findById(userId).orElse(null);
        long   xp    = user != null ? user.getXp()    : completedConcepts * 50L;
        int    level = user != null ? user.getLevel()  : Math.max(1, (int)(xp / 200));
        String rank  = user != null ? user.getRank()   : computeRank(xp);

        // ── Per-subject progress ─────────────────────────────────────────────
        // Count completed concepts per subject in memory (no N countByUserIdAndSubjectId queries)
        Map<String, Long> completedBySubject = allProgress.stream()
                .collect(Collectors.groupingBy(UserConceptProgress::getSubjectId, Collectors.counting()));

        // One badge query instead of N existsByUserIdAndSubjectId calls
        Set<String> badgeSubjectIds = badgeRepository.findByUserId(userId).stream()
                .map(b -> b.getSubjectId())
                .collect(Collectors.toSet());

        // Subject list from Caffeine (0 DB — warmed on startup by CacheWarmup)
        List<Subject> subjects = cacheService.get("subjects", "all", subjectRepository::findAll);

        List<ProgressSummaryDTO.SubjectProgress> subjectProgress = subjects.stream()
                .map(s -> {
                    // Concept count from Caffeine (0 DB — warmed on startup)
                    long total = cacheService.getLong("concepts", "count:" + s.getId(),
                            () -> conceptRepository.countBySubjectId(s.getId()));
                    if (total == 0) return null;
                    long completed = completedBySubject.getOrDefault(s.getId(), 0L);
                    double pct = Math.round((completed * 100.0 / total) * 10) / 10.0;
                    boolean hasBadge = badgeSubjectIds.contains(s.getId());
                    return new ProgressSummaryDTO.SubjectProgress(
                            s.getId(), s.getTitle(), s.getIcon(), s.getColor(),
                            s.getRank() != null ? s.getRank() : "E",
                            (int) total, completed, pct, hasBadge);
                })
                .filter(sp -> sp != null)
                .collect(Collectors.toList());

        // ── Today's concept completion (cross-device quest sync) ────────────
        boolean completedConceptToday = quizAttemptRepository
                .existsByUserIdAndTypeAndPassedTrueAndTakenAtAfter(
                        userId, "CONCEPT", LocalDate.now(IST).atStartOfDay());

        return new ProgressSummaryDTO(totalConcepts, completedConcepts, percentage,
                streak, xp, level, rank, completedConceptToday, subjectProgress);
    }

    public Map<String, Object> getHunterStats(String userId) {
        // Short-TTL cache: hunter stats are read on every dashboard / profile open but only
        // change on a quiz pass or concept uncomplete, both of which evict this key.
        return cacheService.get("hunterStats", userId, () -> buildHunterStats(userId));
    }

    private Map<String, Object> buildHunterStats(String userId) {
        // ── Badges joined with subject info (subject from Caffeine cache) ─────
        List<Map<String, Object>> badges = badgeRepository.findByUserId(userId).stream()
                .map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("subjectId", b.getSubjectId());
                    m.put("score",     b.getScore());
                    m.put("total",     b.getTotal());
                    m.put("earnedAt",  b.getEarnedAt() != null ? b.getEarnedAt().toString() : "");
                    Subject s = cacheService.get("subjects", "id:" + b.getSubjectId(),
                            () -> subjectRepository.findById(b.getSubjectId()).orElse(null));
                    if (s != null) {
                        m.put("title", s.getTitle());
                        m.put("icon",  s.getIcon() != null  ? s.getIcon()  : "📚");
                        m.put("color", s.getColor() != null ? s.getColor() : "#9B6ED4");
                        m.put("rank",  s.getRank() != null  ? s.getRank()  : "E");
                    }
                    return m;
                })
                .collect(Collectors.toList());

        // ── Roadmap badges joined with roadmap info (roadmap from Caffeine cache) ─
        List<Map<String, Object>> roadmapBadges = roadmapBadgeRepository.findByUserId(userId).stream()
                .map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("roadmapId", b.getRoadmapId());
                    m.put("badge",     b.getBadge());
                    m.put("score",     b.getScore());
                    m.put("total",     b.getTotal());
                    m.put("earnedAt",  b.getEarnedAt() != null ? b.getEarnedAt().toString() : "");
                    m.put("type",      "ROADMAP");
                    Roadmap r = cacheService.get("roadmaps", "id:" + b.getRoadmapId(),
                            () -> roadmapRepository.findById(b.getRoadmapId()).orElse(null));
                    if (r != null) {
                        m.put("title", r.getTitle());
                        m.put("icon",  r.getIcon() != null  ? r.getIcon()  : "🗺️");
                        m.put("color", r.getColor() != null ? r.getColor() : "#9B6ED4");
                    }
                    return m;
                })
                .collect(Collectors.toList());

        // ── Mark subject badges with type ─────────────────────────────────────
        badges.forEach(b -> b.put("type", "SUBJECT"));

        // ── Quiz counts ───────────────────────────────────────────────────────
        long conceptPassed   = quizAttemptRepository.countByUserIdAndTypeAndPassedTrue(userId, "CONCEPT");
        long subjectPassed   = badges.size();
        long roadmapPassed   = roadmapBadges.size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("badges",           badges);
        result.put("roadmapBadges",    roadmapBadges);
        result.put("conceptsPassed",   conceptPassed);
        result.put("subjectsPassed",   subjectPassed);
        result.put("roadmapsPassed",   roadmapPassed);
        return result;
    }
}
