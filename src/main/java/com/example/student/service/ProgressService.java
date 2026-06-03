package com.example.student.service;

import com.example.student.dto.ProgressSummaryDTO;
import com.example.student.exception.ResourceNotFoundException;
import com.example.student.model.Concept;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    public ProgressService(UserConceptProgressRepository progressRepository,
                           ConceptRepository conceptRepository,
                           SubjectRepository subjectRepository,
                           UserRepository userRepository,
                           QuizAttemptRepository quizAttemptRepository,
                           UserSubjectBadgeRepository badgeRepository,
                           UserRoadmapBadgeRepository roadmapBadgeRepository,
                           RoadmapRepository roadmapRepository) {
        this.progressRepository = progressRepository;
        this.conceptRepository = conceptRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.badgeRepository = badgeRepository;
        this.roadmapBadgeRepository = roadmapBadgeRepository;
        this.roadmapRepository = roadmapRepository;
    }

    private static String computeRank(long xp) {
        if (xp >= 10000) return "S";
        if (xp >= 6000)  return "A";
        if (xp >= 3000)  return "B";
        if (xp >= 1500)  return "C";
        if (xp >= 500)   return "D";
        return "E";
    }

    /**
     * Marks a concept complete and awards XP.
     * @param quizScore the raw quiz score (e.g. 8 for 8/10). Pass 0 for non-quiz completions.
     * @return map with xpEarned and dailyBonusEarned keys
     */
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
        progress.setCompletedAt(LocalDateTime.now());
        progressRepository.save(progress);

        // XP from quiz score: score * 10 (8/10 → 80, 9/10 → 90, 10/10 → 100)
        int conceptXp = quizScore * 10;

        // Daily bonus: +50 XP if this is the FIRST concept passed today.
        // Uses QuizAttempt.takenAt (reliable timestamp set at submission time)
        // rather than completedAt (which could be corrupted by DataSeeder repairs).
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        boolean isFirstToday = !quizAttemptRepository
                .existsByUserIdAndTypeAndPassedTrueAndTakenAtAfterAndRefIdNot(
                        userId, "CONCEPT", startOfToday, conceptId);
        int dailyBonus = isFirstToday ? 50 : 0;
        int totalXp = conceptXp + dailyBonus;

        // Update User document
        userRepository.findById(userId).ifPresent(user -> {
            long newXp = user.getXp() + totalXp;
            user.setXp(newXp);
            user.setLevel(Math.max(1, (int)(newXp / 200)));
            user.setRank(computeRank(newXp));
            userRepository.save(user);
        });

        return Map.of("message", "Completed", "conceptId", conceptId,
                "xpEarned", totalXp, "dailyBonusEarned", isFirstToday);
    }

    /** Backward-compat overload for non-quiz completions (admin etc.) */
    public Map<String, Object> completeConcept(String conceptId, String userId) {
        return completeConcept(conceptId, userId, 0);
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
        return amount;
    }

    public Map<String, Object> uncompleteConcept(String conceptId, String userId) {
        progressRepository.deleteByUserIdAndConceptId(userId, conceptId);
        return Map.of("message", "Unmarked");
    }

    public ProgressSummaryDTO getProgressSummary(String userId) {
        long totalConcepts     = conceptRepository.count();
        long completedConcepts = progressRepository.countByUserId(userId);
        double percentage = totalConcepts > 0
                ? Math.round((completedConcepts * 100.0 / totalConcepts) * 10) / 10.0
                : 0;

        // ── Streak ──────────────────────────────────────────────────────────
        List<UserConceptProgress> all = progressRepository.findByUserId(userId);
        Set<LocalDate> completionDates = all.stream()
                .filter(p -> p.getCompletedAt() != null)
                .map(p -> p.getCompletedAt().toLocalDate())
                .collect(Collectors.toSet());

        int streak = 0;
        LocalDate date = LocalDate.now();
        while (completionDates.contains(date)) { streak++; date = date.minusDays(1); }

        // ── XP / Level / Rank — read from User document (source of truth) ─────
        User user = userRepository.findById(userId).orElse(null);
        long   xp    = user != null ? user.getXp()    : completedConcepts * 50L;
        int    level = user != null ? user.getLevel()  : Math.max(1, (int)(xp / 200));
        String rank  = user != null ? user.getRank()   : computeRank(xp);

        // ── Per-subject progress ─────────────────────────────────────────────
        List<ProgressSummaryDTO.SubjectProgress> subjectProgress = subjectRepository.findAll()
                .stream()
                .filter(s -> conceptRepository.countBySubjectId(s.getId()) > 0)
                .map(s -> {
                    int total = (int) conceptRepository.countBySubjectId(s.getId());
                    long completed = progressRepository.countByUserIdAndSubjectId(userId, s.getId());
                    double pct = total > 0
                            ? Math.round((completed * 100.0 / total) * 10) / 10.0
                            : 0;
                    boolean hasBadge = badgeRepository.existsByUserIdAndSubjectId(userId, s.getId());
                    return new ProgressSummaryDTO.SubjectProgress(
                            s.getId(), s.getTitle(), s.getIcon(), s.getColor(),
                            s.getRank() != null ? s.getRank() : "E",
                            total, completed, pct, hasBadge);
                })
                .collect(Collectors.toList());

        // ── Today's concept completion (cross-device quest sync) ────────────
        boolean completedConceptToday = quizAttemptRepository
                .existsByUserIdAndTypeAndPassedTrueAndTakenAtAfter(
                        userId, "CONCEPT", LocalDate.now().atStartOfDay());

        ProgressSummaryDTO dto = new ProgressSummaryDTO(totalConcepts, completedConcepts, percentage,
                streak, xp, level, rank, completedConceptToday, subjectProgress);
        return dto;
    }

    public Map<String, Object> getHunterStats(String userId) {
        // ── Badges joined with subject info ──────────────────────────────────
        List<Map<String, Object>> badges = badgeRepository.findByUserId(userId).stream()
                .map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("subjectId", b.getSubjectId());
                    m.put("score",     b.getScore());
                    m.put("total",     b.getTotal());
                    m.put("earnedAt",  b.getEarnedAt() != null ? b.getEarnedAt().toString() : "");
                    subjectRepository.findById(b.getSubjectId()).ifPresent(s -> {
                        m.put("title", s.getTitle());
                        m.put("icon",  s.getIcon() != null  ? s.getIcon()  : "📚");
                        m.put("color", s.getColor() != null ? s.getColor() : "#9B6ED4");
                        m.put("rank",  s.getRank() != null  ? s.getRank()  : "E");
                    });
                    return m;
                })
                .collect(Collectors.toList());

        // ── Roadmap badges joined with roadmap info ───────────────────────────
        List<Map<String, Object>> roadmapBadges = roadmapBadgeRepository.findByUserId(userId).stream()
                .map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("roadmapId", b.getRoadmapId());
                    m.put("badge",     b.getBadge());
                    m.put("score",     b.getScore());
                    m.put("total",     b.getTotal());
                    m.put("earnedAt",  b.getEarnedAt() != null ? b.getEarnedAt().toString() : "");
                    m.put("type",      "ROADMAP");
                    roadmapRepository.findById(b.getRoadmapId()).ifPresent(r -> {
                        m.put("title", r.getTitle());
                        m.put("icon",  r.getIcon() != null  ? r.getIcon()  : "🗺️");
                        m.put("color", r.getColor() != null ? r.getColor() : "#9B6ED4");
                    });
                    return m;
                })
                .collect(Collectors.toList());

        // ── Mark subject badges with type ─────────────────────────────────────
        badges.forEach(b -> b.put("type", "SUBJECT"));

        // ── Quiz counts ───────────────────────────────────────────────────────
        long conceptPassed   = quizAttemptRepository.findByUserIdAndTypeAndPassedTrue(userId, "CONCEPT").size();
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
