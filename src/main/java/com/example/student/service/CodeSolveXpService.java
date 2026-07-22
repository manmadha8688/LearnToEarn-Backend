package com.example.student.service;

import com.example.student.model.ProblemQuestion;
import com.example.student.model.User;
import com.example.student.repository.UserRepository;
import com.example.student.security.UserDetailsServiceImpl;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Awards one-time XP for solving a Code Gym problem. The first Submit that passes
 * every test case (sample + hidden) grants XP once and marks the problem solved;
 * re-solving never re-awards and XP is never reversed — {@link User#getSolvedProblemIds()}
 * is the ledger (mirrors {@link ProfileXpService}).
 *
 * <p>XP scales with the problem's {@code track} (overall gym tier) plus its {@code level}
 * (difficulty within the track): {@code xp = trackWeight + levelBonus} → 15..65 per problem.
 * Guests never earn (their accounts are ephemeral). On a first solve the progress-summary,
 * hunter-stats and cached auth principal are evicted so the new XP/rank shows immediately
 * and a stale principal can't double-award.
 */
@Service
public class CodeSolveXpService {

    private final UserRepository userRepository;
    private final ProgressService progressService;
    private final CacheService cacheService;
    private final UserDetailsServiceImpl userDetailsService;
    private final MongoTemplate mongoTemplate;

    public CodeSolveXpService(UserRepository userRepository,
                              ProgressService progressService,
                              CacheService cacheService,
                              UserDetailsServiceImpl userDetailsService,
                              MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.progressService = progressService;
        this.cacheService = cacheService;
        this.userDetailsService = userDetailsService;
        this.mongoTemplate = mongoTemplate;
    }

    /** Outcome carried to the judge response for the client toast / "Solved" state. */
    public record SolveResult(boolean solved, boolean firstSolve, int xpEarned, long newXp,
                              String rank, boolean rankUp) {
        static SolveResult none() { return new SolveResult(false, false, 0, 0, "E", false); }
    }

    /**
     * Record a passing Submit. The "mark solved" step is an atomic {@code $addToSet}
     * so the once-only XP award is race-proof: if two submits for the same unsolved
     * problem arrive at the same millisecond, only ONE gets modifiedCount == 1 and
     * awards XP; the other sees modifiedCount == 0 and returns firstSolve = false.
     * Guests are excluded by the role criteria (ephemeral accounts never earn).
     */
    public SolveResult award(String userId, ProblemQuestion problem) {
        if (userId == null || problem == null || problem.getId() == null) return SolveResult.none();
        String problemId = problem.getId();

        // Atomic, race-proof claim of the first solve. Matches a non-guest user and
        // only mutates when problemId was NOT already in the set.
        Query query = new Query(Criteria.where("_id").is(userId).and("role").ne("GUEST"));
        Update update = new Update().addToSet("solvedProblemIds", problemId);
        UpdateResult result = mongoTemplate.updateFirst(query, update, User.class);

        if (result.getModifiedCount() == 0) {
            // Already solved, or a guest, or no such user → award nothing, no reload cost
            // unless we need to report the existing solved/xp state to the client.
            User existing = userRepository.findById(userId).orElse(null);
            if (existing == null) return SolveResult.none();
            boolean already = existing.getSolvedProblemIds() != null
                    && existing.getSolvedProblemIds().contains(problemId);
            return new SolveResult(already, false, 0, existing.getXp(),
                    existing.getRank() != null ? existing.getRank() : "E", false);
        }

        // We just added the id → this is the first solve. Apply XP + rank via $set
        // (not a full-document save) so a concurrent stale principal save can't wipe
        // the solvedProblemIds we just wrote with $addToSet.
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return SolveResult.none();

        String rankBefore = user.getRank() != null ? user.getRank() : "E";
        int xp = xpFor(problem.getTrack(), problem.getLevel());
        progressService.applyXp(user, xp); // mutates xp/level/rank in-memory

        String rankAfter = user.getRank() != null ? user.getRank() : "E";
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(userId)),
                new Update()
                        .set("xp", user.getXp())
                        .set("level", user.getLevel())
                        .set("rank", rankAfter),
                User.class);

        cacheService.evict("progress", "summary:" + userId);
        cacheService.evict("hunterStats", userId);
        cacheService.evict("dashboardBootstrap", userId);
        userDetailsService.evict(user.getUsername());

        boolean rankUp = !rankAfter.equals(rankBefore);
        return new SolveResult(true, true, xp, user.getXp(), rankAfter, rankUp);
    }

    // ── XP matrix: xp = trackWeight + levelBonus (15..65) ──────────────────────
    static int xpFor(String track, String level) {
        return trackWeight(track) + levelBonus(level);
    }

    private static int trackWeight(String track) {
        return switch (track == null ? "" : track.trim().toUpperCase()) {
            case "START_CODING"   -> 5;
            case "LOGIC_BUILDING" -> 10;
            case "SKILL_UP"       -> 15;
            case "CRACK_IT"       -> 20;
            case "BUILD_IT"       -> 25;
            case "PROVE_IT"       -> 30;
            default               -> 5;
        };
    }

    private static int levelBonus(String level) {
        return switch (level == null ? "" : level.trim().toUpperCase()) {
            case "INTERMEDIATE" -> 20;
            case "ADVANCED"     -> 35;
            default             -> 10; // BEGINNER / unknown
        };
    }
}
