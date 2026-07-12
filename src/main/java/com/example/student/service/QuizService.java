package com.example.student.service;

import com.example.student.config.QuizConstants;
import com.example.student.dto.*;
import com.example.student.exception.ResourceNotFoundException;
import com.example.student.model.*;
import com.example.student.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuizService {

    // Quiz timestamps + retry cooldowns use IST so "today", daily bonuses, and cooldown
    // windows stay consistent with ProgressService regardless of server timezone (Render = UTC).
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final QuestionRepository questionRepo;
    private final QuizAttemptRepository attemptRepo;
    private final UserSubjectBadgeRepository badgeRepo;
    private final UserRoadmapBadgeRepository roadmapBadgeRepo;
    private final ConceptRepository conceptRepo;
    private final SubjectRepository subjectRepo;
    private final RoadmapSubjectRepository roadmapSubjectRepo;
    private final RoadmapRepository roadmapRepo;
    private final ProgressService progressService;
    private final UserConceptProgressRepository progressRepo;
    private final CertificateService certificateService;
    private final CacheService cacheService;

    public QuizService(QuestionRepository questionRepo,
                       QuizAttemptRepository attemptRepo,
                       UserSubjectBadgeRepository badgeRepo,
                       UserRoadmapBadgeRepository roadmapBadgeRepo,
                       ConceptRepository conceptRepo,
                       SubjectRepository subjectRepo,
                       RoadmapSubjectRepository roadmapSubjectRepo,
                       RoadmapRepository roadmapRepo,
                       ProgressService progressService,
                       UserConceptProgressRepository progressRepo,
                       CertificateService certificateService,
                       CacheService cacheService) {
        this.questionRepo = questionRepo;
        this.attemptRepo = attemptRepo;
        this.badgeRepo = badgeRepo;
        this.roadmapBadgeRepo = roadmapBadgeRepo;
        this.conceptRepo = conceptRepo;
        this.subjectRepo = subjectRepo;
        this.roadmapSubjectRepo = roadmapSubjectRepo;
        this.roadmapRepo = roadmapRepo;
        this.progressService = progressService;
        this.progressRepo = progressRepo;
        this.certificateService = certificateService;
        this.cacheService = cacheService;
    }

    // ─── START ────────────────────────────────────────────────────────────────

    public QuizStartResponse startConceptQuiz(String conceptId, String userId) {
        checkCooldown("CONCEPT", conceptId, userId);
        List<Question> all = questionRepo.findByConceptId(conceptId);
        if (all.isEmpty()) throw new RuntimeException("No questions available for this concept yet");
        Collections.shuffle(all);
        List<Question> picked = all.stream().limit(QuizConstants.CONCEPT_TOTAL).collect(Collectors.toList());
        return buildStartResponse("CONCEPT", conceptId, null, picked);
    }

    public QuizStartResponse startSubjectQuiz(String subjectId, String userId) {
        checkCooldown("SUBJECT", subjectId, userId);
        List<Concept> concepts = conceptRepo.findBySubjectIdOrderByOrderIndex(subjectId);
        List<String> conceptIds = concepts.stream().map(Concept::getId).collect(Collectors.toList());
        List<Question> all = conceptIds.isEmpty()
                ? List.of()
                : questionRepo.findByConceptIdIn(conceptIds);
        if (all.isEmpty()) throw new RuntimeException("No questions available for this subject yet");
        Collections.shuffle(all);
        List<Question> picked = all.stream().limit(QuizConstants.SUBJECT_TOTAL).collect(Collectors.toList());
        return buildStartResponse("SUBJECT", subjectId, QuizConstants.SUBJECT_TIME_MINUTES, picked);
    }

    public QuizStartResponse startRoadmapQuiz(String roadmapId, String userId) {
        checkCooldown("ROADMAP", roadmapId, userId);
        List<RoadmapSubject> roadmapSubjects = roadmapSubjectRepo.findByRoadmapIdOrderByOrderIndex(roadmapId);
        List<String> subjectIds = roadmapSubjects.stream()
                .map(RoadmapSubject::getSubjectId)
                .collect(Collectors.toList());
        List<Question> all = subjectIds.isEmpty()
                ? List.of()
                : questionRepo.findBySubjectIdIn(subjectIds);
        if (all.isEmpty()) throw new RuntimeException("No questions available for this roadmap yet");
        Collections.shuffle(all);
        List<Question> picked = all.stream().limit(QuizConstants.ROADMAP_TOTAL).collect(Collectors.toList());
        return buildStartResponse("ROADMAP", roadmapId, QuizConstants.ROADMAP_TIME_MINUTES, picked);
    }

    private QuizStartResponse buildStartResponse(String type, String refId, Integer timeLimit, List<Question> questions) {
        List<QuizQuestionDTO> dtos = questions.stream()
                .map(q -> new QuizQuestionDTO(q.getId(), q.getText(), q.getOptions()))
                .collect(Collectors.toList());
        return new QuizStartResponse(UUID.randomUUID().toString(), type, refId, dtos.size(), timeLimit, dtos);
    }

    // ─── SUBMIT ───────────────────────────────────────────────────────────────

    @Transactional
    public QuizResultResponse submitQuiz(QuizSubmitRequest req, String userId) {
        String type = req.getType() == null ? "" : req.getType().toUpperCase();
        String refId = req.getRefId();
        List<String> qIds = req.getQuestionIds();
        List<Integer> answers = req.getAnswers();

        // Load the legitimate question pool for this quiz and validate the submission
        // against it. This stops a client from substituting, duplicating, dropping, or
        // injecting questions to game the fixed pass threshold — the correct answers were
        // already checked server-side, but the *set* of questions must be trusted too.
        List<Question> pool = loadQuestionPool(type, refId);
        if (pool.isEmpty()) throw new RuntimeException("No questions available for this quiz");
        Map<String, Question> qMap = pool.stream()
                .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));
        int expectedTotal = Math.min(pool.size(), totalFor(type));
        validateSubmission(qIds, answers, qMap.keySet(), expectedTotal);

        int score = 0;
        List<QuizAnswerResult> results = new ArrayList<>();
        for (int i = 0; i < qIds.size(); i++) {
            Question q = qMap.get(qIds.get(i));
            if (q == null) continue;
            int studentAns = (i < answers.size() && answers.get(i) != null) ? answers.get(i) : -1;
            boolean correct = (studentAns == q.getCorrectIndex());
            if (correct) score++;
            results.add(new QuizAnswerResult(q.getId(), q.getText(), q.getOptions(),
                    studentAns, q.getCorrectIndex(), correct, q.getExplanation()));
        }

        int total = qIds.size();
        boolean passed = switch (type) {
            case "CONCEPT" -> score >= QuizConstants.CONCEPT_PASS;
            case "SUBJECT" -> score >= QuizConstants.SUBJECT_PASS;
            case "ROADMAP" -> score >= QuizConstants.ROADMAP_INTERVIEW_READY;
            default -> false;
        };

        LocalDateTime nextRetryAt = null;
        if (!passed) {
            nextRetryAt = switch (type) {
                case "CONCEPT" -> LocalDateTime.now(IST).plusMinutes(QuizConstants.CONCEPT_RETRY_MINUTES);
                case "SUBJECT" -> LocalDateTime.now(IST).plusHours(QuizConstants.SUBJECT_RETRY_HOURS);
                default -> LocalDateTime.now(IST).plusHours(QuizConstants.ROADMAP_RETRY_HOURS);
            };
        }

        QuizAttempt attempt = new QuizAttempt();
        attempt.setUserId(userId);
        attempt.setType(type);
        attempt.setRefId(refId);
        attempt.setQuestionIds(qIds);
        attempt.setAnswers(answers);
        attempt.setScore(score);
        attempt.setTotal(total);
        attempt.setPassed(passed);
        attempt.setTakenAt(LocalDateTime.now(IST));
        attempt.setNextRetryAt(nextRetryAt);
        // Complete concept BEFORE saving attempt so that if completeConcept fails,
        // the attempt is not recorded (user can retry). This prevents the inconsistency
        // where attempt.passed=true but UserConceptProgress is missing.
        int xpEarned = 0;
        boolean dailyBonus = false;
        String badge = null;
        if (passed) {
            if ("CONCEPT".equals(type)) {
                Map<String, Object> result = progressService.completeConcept(refId, userId, score);
                xpEarned   = (int) result.getOrDefault("xpEarned", 0);
                dailyBonus = (boolean) result.getOrDefault("dailyBonusEarned", false);
            } else if ("SUBJECT".equals(type)) {
                badge = "SUBJECT_MASTERED";
                var existing = badgeRepo.findByUserIdAndSubjectId(userId, refId);
                UserSubjectBadge b = existing.orElse(new UserSubjectBadge(null, userId, refId, 0, 0, null));
                boolean improved = existing.isEmpty() || score > b.getScore();
                if (improved) {
                    b.setScore(score);
                    b.setTotal(total);
                    b.setEarnedAt(LocalDateTime.now(IST));
                    badgeRepo.save(b);
                    xpEarned = progressService.awardXp(userId, score * 10);
                }
                // Mint / update the Subject Mastery certificate.
                certificateService.issueSubjectCertificate(userId, refId, score, total);
            } else if ("ROADMAP".equals(type)) {
                badge = score >= QuizConstants.ROADMAP_JOB_READY ? "JOB_READY" : "INTERVIEW_READY";
                var existing = roadmapBadgeRepo.findByUserIdAndRoadmapId(userId, refId);
                UserRoadmapBadge rb = existing.orElse(new UserRoadmapBadge(null, userId, refId, badge, 0, 0, null));
                boolean improved = existing.isEmpty() || score > rb.getScore();
                if (improved) {
                    rb.setBadge(badge);
                    rb.setScore(score);
                    rb.setTotal(total);
                    rb.setEarnedAt(LocalDateTime.now(IST));
                    roadmapBadgeRepo.save(rb);
                    xpEarned = progressService.awardXp(userId, score * 10);
                }
                // Mint / update the Career Path Completion certificate.
                certificateService.issueRoadmapCertificate(userId, refId, badge, score, total);
            }
        }

        attempt.setXpEarned(xpEarned);
        attempt.setDailyBonusEarned(dailyBonus);
        QuizAttempt saved = attemptRepo.save(attempt);

        return new QuizResultResponse(saved.getId(), score, total, passed, badge, nextRetryAt, results, xpEarned, dailyBonus);
    }

    // ─── GET ATTEMPT RESULT ───────────────────────────────────────────────────

    public QuizResultResponse getAttemptResult(String attemptId, String userId) {
        QuizAttempt attempt = attemptRepo.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
        if (!attempt.getUserId().equals(userId))
            throw new RuntimeException("Access denied");

        List<Question> questions = questionRepo.findAllById(attempt.getQuestionIds());
        Map<String, Question> qMap = questions.stream().collect(Collectors.toMap(Question::getId, q -> q));

        List<QuizAnswerResult> results = new ArrayList<>();
        for (int i = 0; i < attempt.getQuestionIds().size(); i++) {
            Question q = qMap.get(attempt.getQuestionIds().get(i));
            if (q == null) continue;
            int studentAns = (i < attempt.getAnswers().size()) ? attempt.getAnswers().get(i) : -1;
            boolean correct = (studentAns == q.getCorrectIndex());
            results.add(new QuizAnswerResult(q.getId(), q.getText(), q.getOptions(),
                    studentAns, q.getCorrectIndex(), correct, q.getExplanation()));
        }

        String badge = null;
        if (attempt.isPassed()) {
            if ("SUBJECT".equals(attempt.getType())) badge = "SUBJECT_MASTERED";
            else if ("ROADMAP".equals(attempt.getType())) {
                badge = attempt.getScore() >= QuizConstants.ROADMAP_JOB_READY ? "JOB_READY" : "INTERVIEW_READY";
            }
        }

        return new QuizResultResponse(attempt.getId(), attempt.getScore(), attempt.getTotal(),
                attempt.isPassed(), badge, attempt.getNextRetryAt(), results,
                attempt.getXpEarned(), attempt.isDailyBonusEarned());
    }

    // ─── CAN RETRY ────────────────────────────────────────────────────────────

    public QuizAttemptResponse getAttemptStatus(String type, String refId, String userId) {
        String typeUpper = type.toUpperCase();
        List<QuizAttempt> all = attemptRepo.findByUserIdAndTypeAndRefId(userId, typeUpper, refId);
        if (all.isEmpty()) {
            return new QuizAttemptResponse(true, false, 0, 0, 0, null, null);
        }

        Optional<QuizAttempt> best = all.stream().max(Comparator.comparingInt(QuizAttempt::getScore));
        Optional<QuizAttempt> latest = all.stream().max(Comparator.comparing(QuizAttempt::getTakenAt));
        boolean hasPassed = all.stream().anyMatch(QuizAttempt::isPassed);

        boolean canRetry = true;
        LocalDateTime nextRetryAt = null;
        if (latest.isPresent() && !latest.get().isPassed() && latest.get().getNextRetryAt() != null) {
            if (LocalDateTime.now(IST).isBefore(latest.get().getNextRetryAt())) {
                canRetry = false;
                nextRetryAt = latest.get().getNextRetryAt();
            }
        }

        return new QuizAttemptResponse(
                canRetry,
                hasPassed,
                best.map(QuizAttempt::getScore).orElse(0),
                best.map(QuizAttempt::getTotal).orElse(0),
                all.size(),
                nextRetryAt,
                latest.map(QuizAttempt::getTakenAt).orElse(null)
        );
    }

    // ─── UNLOCK STATUS ────────────────────────────────────────────────────────

    public Map<String, Object> getSubjectStatus(String subjectId, String userId) {
        List<Concept> concepts = conceptRepo.findBySubjectIdOrderByOrderIndex(subjectId);
        int conceptCount = concepts.size();
        long completed = progressRepo.countByUserIdAndSubjectId(userId, subjectId);
        boolean allMastered = conceptCount > 0 && completed >= conceptCount;

        Optional<UserSubjectBadge> badge = badgeRepo.findByUserIdAndSubjectId(userId, subjectId);

        // Latest attempt + cooldown so the gate can show last score and "next attempt in …".
        List<QuizAttempt> attempts = attemptRepo.findByUserIdAndTypeAndRefId(userId, "SUBJECT", subjectId);
        QuizAttempt latest = attempts.stream().max(Comparator.comparing(QuizAttempt::getTakenAt)).orElse(null);
        boolean canRetry = true;
        LocalDateTime nextRetryAt = null;
        if (latest != null && !latest.isPassed() && latest.getNextRetryAt() != null
                && LocalDateTime.now(IST).isBefore(latest.getNextRetryAt())) {
            canRetry = false;
            nextRetryAt = latest.getNextRetryAt();
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("allConceptsMastered", allMastered);
        m.put("hasBadge", badge.isPresent());
        m.put("badgeScore", badge.map(UserSubjectBadge::getScore).orElse(0));
        m.put("badgeTotal", badge.map(UserSubjectBadge::getTotal).orElse(0));
        m.put("conceptCount", conceptCount);
        m.put("attemptCount", attempts.size());
        m.put("lastScore", latest != null ? latest.getScore() : 0);
        m.put("lastTotal", latest != null ? latest.getTotal() : 0);
        m.put("lastPassed", latest != null && latest.isPassed());
        m.put("canRetry", canRetry);
        m.put("nextRetryAt", nextRetryAt != null ? nextRetryAt.toString() : null);
        return m;
    }

    // ─── BULK SUBJECT STATUS — replaces N×M DB calls with 2 queries ─────────────
    public Map<String, Object> getBulkSubjectStatus(List<String> subjectIds, String userId) {
        // Query 1: all badges for this user (one round-trip)
        Map<String, UserSubjectBadge> badgeMap = badgeRepo.findByUserId(userId)
                .stream().collect(Collectors.toMap(UserSubjectBadge::getSubjectId, b -> b));

        // Query 2: all concept progress for this user (one round-trip)
        Map<String, Long> progressCounts = progressRepo.findByUserId(userId)
                .stream().collect(Collectors.groupingBy(
                        com.example.student.model.UserConceptProgress::getSubjectId,
                        Collectors.counting()));

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (String subjectId : subjectIds) {
            UserSubjectBadge badge = badgeMap.get(subjectId);
            // Concept counts served from Caffeine (warmed on startup, never a DB call)
            long conceptCount = cacheService.getLong("concepts", "count:" + subjectId,
                    () -> (long) conceptRepo.countBySubjectId(subjectId));
            long completed = progressCounts.getOrDefault(subjectId, 0L);

            Map<String, Object> status = new java.util.LinkedHashMap<>();
            status.put("allConceptsMastered", conceptCount > 0 && completed >= conceptCount);
            status.put("hasBadge",    badge != null);
            status.put("badgeScore",  badge != null ? badge.getScore() : 0);
            status.put("badgeTotal",  badge != null ? badge.getTotal() : 0);
            status.put("conceptCount", (int) conceptCount);
            result.put(subjectId, status);
        }
        return result;
    }

    public Map<String, Object> getRoadmapStatus(String roadmapId, String userId) {
        List<RoadmapSubject> roadmapSubjects = roadmapSubjectRepo.findByRoadmapIdOrderByOrderIndex(roadmapId);
        Set<String> badgeSubjectIds = badgeRepo.findByUserId(userId).stream()
                .map(UserSubjectBadge::getSubjectId)
                .collect(Collectors.toSet());
        boolean allSubjectsDone = !roadmapSubjects.isEmpty() && roadmapSubjects.stream()
                .allMatch(rs -> badgeSubjectIds.contains(rs.getSubjectId()));
        java.util.Optional<UserRoadmapBadge> rb = roadmapBadgeRepo.findByUserIdAndRoadmapId(userId, roadmapId);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("allSubjectsDone", allSubjectsDone);
        result.put("hasBadge",  rb.isPresent());
        result.put("badge",     rb.map(UserRoadmapBadge::getBadge).orElse(null));
        result.put("badgeScore", rb.map(UserRoadmapBadge::getScore).orElse(0));
        result.put("badgeTotal", rb.map(UserRoadmapBadge::getTotal).orElse(0));
        return result;
    }

    // ─── ATTEMPT HISTORY ────────────────────────────────────────────────────────
    /**
     * Full quiz/test attempt history for a student, newest first, enriched with the
     * concept/subject/career-path name so the arena can show "what I attempted, my
     * scores and when". {@code limit <= 0} returns everything.
     */
    public List<Map<String, Object>> getQuizHistory(String userId, int limit) {
        List<QuizAttempt> attempts = attemptRepo.findByUserIdOrderByTakenAtDesc(userId);
        if (attempts.isEmpty()) return List.of();

        Set<String> conceptIds = new HashSet<>();
        Set<String> subjectIds = new HashSet<>();
        Set<String> roadmapIds = new HashSet<>();
        for (QuizAttempt a : attempts) {
            switch (a.getType() == null ? "" : a.getType()) {
                case "CONCEPT" -> conceptIds.add(a.getRefId());
                case "SUBJECT" -> subjectIds.add(a.getRefId());
                case "ROADMAP" -> roadmapIds.add(a.getRefId());
                default -> { }
            }
        }
        Map<String, String> conceptNames = new HashMap<>();
        conceptRepo.findAllById(conceptIds).forEach(c -> conceptNames.put(c.getId(), c.getTitle()));
        Map<String, String> subjectNames = new HashMap<>();
        subjectRepo.findAllById(subjectIds).forEach(s -> subjectNames.put(s.getId(), s.getTitle()));
        Map<String, String> roadmapNames = new HashMap<>();
        roadmapRepo.findAllById(roadmapIds).forEach(r -> roadmapNames.put(r.getId(), r.getTitle()));

        List<Map<String, Object>> out = new ArrayList<>();
        for (QuizAttempt a : attempts) {
            String type = a.getType() == null ? "" : a.getType();
            String name = switch (type) {
                case "CONCEPT" -> conceptNames.getOrDefault(a.getRefId(), "Concept");
                case "SUBJECT" -> subjectNames.getOrDefault(a.getRefId(), "Subject");
                case "ROADMAP" -> roadmapNames.getOrDefault(a.getRefId(), "Career Path");
                default -> "Quiz";
            };
            int pct = a.getTotal() > 0 ? (int) Math.round(a.getScore() * 100.0 / a.getTotal()) : 0;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("type", type);
            m.put("refId", a.getRefId());
            m.put("name", name);
            m.put("score", a.getScore());
            m.put("total", a.getTotal());
            m.put("scorePercent", pct);
            m.put("passed", a.isPassed());
            m.put("xpEarned", a.getXpEarned());
            m.put("takenAt", a.getTakenAt() != null ? a.getTakenAt().toString() : null);
            out.add(m);
            if (limit > 0 && out.size() >= limit) break;
        }
        return out;
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    /** The full, legitimate set of questions a given quiz can draw from. */
    private List<Question> loadQuestionPool(String type, String refId) {
        return switch (type) {
            case "CONCEPT" -> questionRepo.findByConceptId(refId);
            case "SUBJECT" -> {
                List<String> conceptIds = conceptRepo.findBySubjectIdOrderByOrderIndex(refId)
                        .stream().map(Concept::getId).collect(Collectors.toList());
                yield conceptIds.isEmpty() ? List.of() : questionRepo.findByConceptIdIn(conceptIds);
            }
            case "ROADMAP" -> {
                List<String> subjectIds = roadmapSubjectRepo.findByRoadmapIdOrderByOrderIndex(refId)
                        .stream().map(RoadmapSubject::getSubjectId).collect(Collectors.toList());
                yield subjectIds.isEmpty() ? List.of() : questionRepo.findBySubjectIdIn(subjectIds);
            }
            default -> List.of();
        };
    }

    private int totalFor(String type) {
        return switch (type) {
            case "CONCEPT" -> QuizConstants.CONCEPT_TOTAL;
            case "SUBJECT" -> QuizConstants.SUBJECT_TOTAL;
            case "ROADMAP" -> QuizConstants.ROADMAP_TOTAL;
            default -> 0;
        };
    }

    /** Reject any submission that doesn't match the exact shape of a real quiz for this ref. */
    private void validateSubmission(List<String> qIds, List<Integer> answers,
                                    Set<String> validIds, int expectedTotal) {
        if (qIds == null || answers == null)
            throw new RuntimeException("Invalid quiz submission");
        if (qIds.size() != answers.size())
            throw new RuntimeException("Invalid quiz submission: answer count mismatch");
        if (expectedTotal <= 0 || qIds.size() != expectedTotal)
            throw new RuntimeException("Invalid quiz submission: unexpected number of questions");
        if (new HashSet<>(qIds).size() != qIds.size())
            throw new RuntimeException("Invalid quiz submission: duplicate questions");
        if (!validIds.containsAll(qIds))
            throw new RuntimeException("Invalid quiz submission: unknown questions");
    }

    private void checkCooldown(String type, String refId, String userId) {
        Optional<QuizAttempt> latest = attemptRepo
                .findTopByUserIdAndTypeAndRefIdOrderByTakenAtDesc(userId, type, refId);
        if (latest.isPresent() && !latest.get().isPassed() && latest.get().getNextRetryAt() != null) {
            if (LocalDateTime.now(IST).isBefore(latest.get().getNextRetryAt())) {
                throw new RuntimeException("Please wait before retrying. Next attempt available at: "
                        + latest.get().getNextRetryAt());
            }
        }
    }
}
