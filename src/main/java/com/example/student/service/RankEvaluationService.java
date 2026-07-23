package com.example.student.service;

import com.example.student.model.Mission;
import com.example.student.model.MissionSubmission;
import com.example.student.model.QuizAttempt;
import com.example.student.model.Resume;
import com.example.student.model.RoadmapSubject;
import com.example.student.model.User;
import com.example.student.model.UserRoadmapBadge;
import com.example.student.model.UserRoadmapEnrollment;
import com.example.student.model.UserSubjectBadge;
import com.example.student.repository.MissionRepository;
import com.example.student.repository.MissionSubmissionRepository;
import com.example.student.repository.QuizAttemptRepository;
import com.example.student.repository.ResumeRepository;
import com.example.student.repository.RoadmapSubjectRepository;
import com.example.student.repository.UserRepository;
import com.example.student.repository.UserRoadmapBadgeRepository;
import com.example.student.repository.UserRoadmapEnrollmentRepository;
import com.example.student.repository.UserSubjectBadgeRepository;
import com.example.student.security.UserDetailsServiceImpl;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes a hunter's <b>rank</b> from multiple categories of real action — not XP alone.
 * A tier is earned only when EVERY one of its conditions is met (XP floor + subjects
 * mastered + coding solved + aptitude mock best + career-path progress + missions completed
 * by rank + a complete profile & resume for B and above).
 *
 * <p>Rank is <b>raise-only</b>: {@link #reevaluate(String)} never lowers a stored rank, so
 * a temporary dip in any pillar (e.g. clearing a profile field) can't demote a hunter, and
 * existing users are grandfathered on their current (historically XP-derived) rank — the new
 * gates only ever <i>add</i> future rank-ups.
 *
 * <p>Thresholds mirror the frontend rank ladder colors in {@code constants/ranks.js}. XP
 * floors match {@code RankUtil} so a category rank never exceeds the XP a hunter has earned.
 */
@Service
public class RankEvaluationService {

    private static final List<String> ORDER = List.of("E", "D", "C", "B", "A", "S");

    // Resume "≈75% complete" proxy: at least this many non-empty sections in the builder data.
    private static final int RESUME_EXPECTED_SECTIONS = 8;
    private static final int RESUME_MIN_SECTIONS = 6; // 6/8 = 75%

    private final UserRepository userRepository;
    private final UserSubjectBadgeRepository subjectBadgeRepo;
    private final UserRoadmapBadgeRepository roadmapBadgeRepo;
    private final UserRoadmapEnrollmentRepository enrollmentRepo;
    private final RoadmapSubjectRepository roadmapSubjectRepo;
    private final MissionRepository missionRepo;
    private final MissionSubmissionRepository submissionRepo;
    private final QuizAttemptRepository attemptRepo;
    private final ResumeRepository resumeRepo;
    private final CacheService cacheService;
    private final UserDetailsServiceImpl userDetailsService;
    private final MongoTemplate mongoTemplate;

    public RankEvaluationService(UserRepository userRepository,
                                 UserSubjectBadgeRepository subjectBadgeRepo,
                                 UserRoadmapBadgeRepository roadmapBadgeRepo,
                                 UserRoadmapEnrollmentRepository enrollmentRepo,
                                 RoadmapSubjectRepository roadmapSubjectRepo,
                                 MissionRepository missionRepo,
                                 MissionSubmissionRepository submissionRepo,
                                 QuizAttemptRepository attemptRepo,
                                 ResumeRepository resumeRepo,
                                 CacheService cacheService,
                                 UserDetailsServiceImpl userDetailsService,
                                 MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.subjectBadgeRepo = subjectBadgeRepo;
        this.roadmapBadgeRepo = roadmapBadgeRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.roadmapSubjectRepo = roadmapSubjectRepo;
        this.missionRepo = missionRepo;
        this.submissionRepo = submissionRepo;
        this.attemptRepo = attemptRepo;
        this.resumeRepo = resumeRepo;
        this.cacheService = cacheService;
        this.userDetailsService = userDetailsService;
        this.mongoTemplate = mongoTemplate;
    }

    /** Result of a rank re-evaluation: the effective rank and whether it rose this call. */
    public record RankResult(String rank, boolean rankUp) {}

    /**
     * Re-evaluate and persist a user's rank (raise-only). Loads the user fresh so all pillars
     * reflect committed state. On a raise, writes only the {@code rank} field, evicts the
     * progress/hunter/dashboard caches + cached auth principal, and reports {@code rankUp=true}.
     * Guests are ignored.
     */
    public RankResult reevaluate(String userId) {
        if (userId == null) return new RankResult("E", false);
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || "GUEST".equals(user.getRole())) {
            return new RankResult(user != null && user.getRank() != null ? user.getRank() : "E", false);
        }
        String current = user.getRank() != null ? user.getRank() : "E";
        String category = computeCategoryRank(user);
        String target = higherOf(current, category);
        if (target.equals(current)) return new RankResult(current, false);

        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(userId)),
                new Update().set("rank", target),
                User.class);
        cacheService.evict("progress", "summary:" + userId);
        cacheService.evict("hunterStats", userId);
        cacheService.evict("dashboardBootstrap", userId);
        if (user.getUsername() != null) userDetailsService.evict(user.getUsername());
        return new RankResult(target, true);
    }

    /** Pure computation of the category-gated rank for a user (no persistence, no eviction). */
    public String computeCategoryRank(User user) {
        if (user == null) return "E";
        String userId = user.getId();

        long xp = user.getXp();

        Set<String> subjectBadgeIds = subjectBadgeRepo.findByUserId(userId).stream()
                .map(UserSubjectBadge::getSubjectId).collect(Collectors.toSet());
        int subjects = subjectBadgeIds.size();

        int code = user.getSolvedProblemIds() != null ? user.getSolvedProblemIds().size() : 0;

        int mockBest = attemptRepo.findByUserIdAndType(userId, "APTITUDE_MOCK").stream()
                .mapToInt(QuizAttempt::getScore).max().orElse(0);

        // ── Career-path progress ─────────────────────────────────────────────
        List<UserRoadmapEnrollment> enrollments = enrollmentRepo.findByUserId(userId);
        int started = enrollments.size();
        int roadmapsAt50 = 0;
        int roadmapsAt70 = 0;
        for (UserRoadmapEnrollment e : enrollments) {
            List<RoadmapSubject> rs = roadmapSubjectRepo.findByRoadmapIdOrderByOrderIndex(e.getRoadmapId());
            if (rs.isEmpty()) continue;
            long done = rs.stream().filter(x -> subjectBadgeIds.contains(x.getSubjectId())).count();
            double frac = (double) done / rs.size();
            if (frac >= 0.50) roadmapsAt50++;
            if (frac >= 0.70) roadmapsAt70++;
        }
        int fullPaths = roadmapBadgeRepo.findByUserId(userId).size();

        // ── Missions completed (either repo or deploy link), grouped by mission rank ──
        List<MissionSubmission> subs = submissionRepo.findByUserId(userId);
        List<String> completedMissionIds = subs.stream()
                .filter(s -> hasText(s.getRepoUrl()) || hasText(s.getDeployUrl()))
                .map(MissionSubmission::getMissionId)
                .distinct().collect(Collectors.toList());
        Map<String, Long> missionsByRank = completedMissionIds.isEmpty()
                ? Map.of()
                : streamToRankCounts(missionRepo.findAllById(completedMissionIds));
        long mE = missionsByRank.getOrDefault("E", 0L);
        long mD = missionsByRank.getOrDefault("D", 0L);
        long mC = missionsByRank.getOrDefault("C", 0L);
        long mB = missionsByRank.getOrDefault("B", 0L);
        long mA = missionsByRank.getOrDefault("A", 0L);
        long mS = missionsByRank.getOrDefault("S", 0L);

        boolean pr = profileResumeComplete(user);

        // Highest tier whose EVERY condition passes (checked top-down).
        if (xp >= 30_000 && subjects >= 15 && code >= 180 && mockBest >= 49
                && fullPaths >= 2 && mA >= 3 && mS >= 2 && pr) return "S";
        if (xp >= 16_000 && subjects >= 10 && code >= 100 && mockBest >= 46
                && fullPaths >= 1 && roadmapsAt50 >= 2 && mB >= 4 && mA >= 2 && pr) return "A";
        if (xp >= 8_000 && subjects >= 6 && code >= 50 && mockBest >= 41
                && roadmapsAt70 >= 1 && started >= 2 && mC >= 5 && mB >= 2 && pr) return "B";
        if (xp >= 3_500 && subjects >= 3 && code >= 20 && mockBest >= 35
                && started >= 1 && mD >= 5 && mC >= 2) return "C";
        if (xp >= 1_000 && subjects >= 1 && code >= 5
                && mE >= 5 && mD >= 2) return "D";
        return "E";
    }

    private Map<String, Long> streamToRankCounts(Iterable<Mission> missions) {
        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        for (Mission m : missions) {
            String r = m.getRank() == null ? "" : m.getRank().trim().toUpperCase();
            if (ORDER.contains(r)) counts.merge(r, 1L, Long::sum);
        }
        return counts;
    }

    /**
     * Profile + resume gate (required for B and above): personal info + education complete,
     * GitHub + LinkedIn connected (portfolio optional), and a featured resume ≈75% complete.
     */
    private boolean profileResumeComplete(User user) {
        boolean personal = user.isPersonalXpAwarded();
        boolean education = user.isEducationXpAwarded();
        boolean github = user.isGithubXpAwarded() || hasText(user.getGithubId());
        boolean linkedin = user.isLinkedinXpAwarded() || hasText(user.getLinkedinUrl());
        return personal && education && github && linkedin && resumeMostlyComplete(user);
    }

    /** A featured resume with at least {@value #RESUME_MIN_SECTIONS} non-empty builder sections. */
    private boolean resumeMostlyComplete(User user) {
        String rid = user.getFeaturedResumeId();
        if (!hasText(rid)) return false;
        Resume r = resumeRepo.findById(rid).orElse(null);
        if (r == null || r.getData() == null || r.getData().isEmpty()) return false;
        long filled = r.getData().values().stream().filter(this::nonEmpty).count();
        // 75% of the expected core sections, but never fewer than RESUME_MIN_SECTIONS filled.
        int needed = Math.min(RESUME_MIN_SECTIONS, Math.max(1, (RESUME_EXPECTED_SECTIONS * 3) / 4));
        return filled >= needed;
    }

    @SuppressWarnings("rawtypes")
    private boolean nonEmpty(Object v) {
        if (v == null) return false;
        if (v instanceof String s) return !s.trim().isEmpty();
        if (v instanceof Collection c) return !c.isEmpty();
        if (v instanceof Map m) return !m.isEmpty();
        return true;
    }

    private boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /** The higher of two rank letters by ladder order (E < D < C < B < A < S). */
    private String higherOf(String a, String b) {
        int ia = Math.max(0, ORDER.indexOf(a == null ? "E" : a));
        int ib = Math.max(0, ORDER.indexOf(b == null ? "E" : b));
        return ORDER.get(Math.max(ia, ib));
    }
}
