package com.example.student.service;

import com.example.student.config.QuizConstants;
import com.example.student.dto.*;
import com.example.student.exception.ResourceNotFoundException;
import com.example.student.model.*;
import com.example.student.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuizService {

    private final QuestionRepository questionRepo;
    private final QuizAttemptRepository attemptRepo;
    private final UserSubjectBadgeRepository badgeRepo;
    private final UserRoadmapBadgeRepository roadmapBadgeRepo;
    private final ConceptRepository conceptRepo;
    private final SubjectRepository subjectRepo;
    private final RoadmapSubjectRepository roadmapSubjectRepo;
    private final ProgressService progressService;

    public QuizService(QuestionRepository questionRepo,
                       QuizAttemptRepository attemptRepo,
                       UserSubjectBadgeRepository badgeRepo,
                       UserRoadmapBadgeRepository roadmapBadgeRepo,
                       ConceptRepository conceptRepo,
                       SubjectRepository subjectRepo,
                       RoadmapSubjectRepository roadmapSubjectRepo,
                       ProgressService progressService) {
        this.questionRepo = questionRepo;
        this.attemptRepo = attemptRepo;
        this.badgeRepo = badgeRepo;
        this.roadmapBadgeRepo = roadmapBadgeRepo;
        this.conceptRepo = conceptRepo;
        this.subjectRepo = subjectRepo;
        this.roadmapSubjectRepo = roadmapSubjectRepo;
        this.progressService = progressService;
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
        List<Question> all = new ArrayList<>();
        for (String cid : conceptIds) all.addAll(questionRepo.findByConceptId(cid));
        if (all.isEmpty()) throw new RuntimeException("No questions available for this subject yet");
        Collections.shuffle(all);
        List<Question> picked = all.stream().limit(QuizConstants.SUBJECT_TOTAL).collect(Collectors.toList());
        return buildStartResponse("SUBJECT", subjectId, QuizConstants.SUBJECT_TIME_MINUTES, picked);
    }

    public QuizStartResponse startRoadmapQuiz(String roadmapId, String userId) {
        checkCooldown("ROADMAP", roadmapId, userId);
        List<RoadmapSubject> roadmapSubjects = roadmapSubjectRepo.findByRoadmapIdOrderByOrderIndex(roadmapId);
        List<Question> all = new ArrayList<>();
        for (RoadmapSubject rs : roadmapSubjects) {
            all.addAll(questionRepo.findBySubjectId(rs.getSubjectId()));
        }
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

    public QuizResultResponse submitQuiz(QuizSubmitRequest req, String userId) {
        String type = req.getType().toUpperCase();
        String refId = req.getRefId();
        List<String> qIds = req.getQuestionIds();
        List<Integer> answers = req.getAnswers();

        List<Question> questions = questionRepo.findAllById(qIds);
        Map<String, Question> qMap = questions.stream().collect(Collectors.toMap(Question::getId, q -> q));

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
                case "CONCEPT" -> LocalDateTime.now().plusMinutes(QuizConstants.CONCEPT_RETRY_MINUTES);
                case "SUBJECT" -> LocalDateTime.now().plusHours(QuizConstants.SUBJECT_RETRY_HOURS);
                default -> LocalDateTime.now().plusHours(QuizConstants.ROADMAP_RETRY_HOURS);
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
        attempt.setTakenAt(LocalDateTime.now());
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
                UserSubjectBadge b = badgeRepo.findByUserIdAndSubjectId(userId, refId)
                        .orElse(new UserSubjectBadge(null, userId, refId, score, total, null));
                if (b.getScore() <= score) {
                    b.setScore(score);
                    b.setTotal(total);
                    b.setEarnedAt(LocalDateTime.now());
                    badgeRepo.save(b);
                }
                xpEarned = progressService.awardXp(userId, score * 10);
            } else if ("ROADMAP".equals(type)) {
                badge = score >= QuizConstants.ROADMAP_JOB_READY ? "JOB_READY" : "INTERVIEW_READY";
                xpEarned = progressService.awardXp(userId, score * 10);
                UserRoadmapBadge rb = roadmapBadgeRepo.findByUserIdAndRoadmapId(userId, refId)
                        .orElse(new UserRoadmapBadge(null, userId, refId, badge, score, total, null));
                if (rb.getScore() <= score) {
                    rb.setBadge(badge);
                    rb.setScore(score);
                    rb.setTotal(total);
                    rb.setEarnedAt(LocalDateTime.now());
                    roadmapBadgeRepo.save(rb);
                }
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
            if (LocalDateTime.now().isBefore(latest.get().getNextRetryAt())) {
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
        boolean allMastered = !concepts.isEmpty() && concepts.stream()
                .allMatch(c -> attemptRepo.existsByUserIdAndTypeAndRefIdAndPassedTrue(userId, "CONCEPT", c.getId()));

        Optional<UserSubjectBadge> badge = badgeRepo.findByUserIdAndSubjectId(userId, subjectId);
        return Map.of(
                "allConceptsMastered", allMastered,
                "hasBadge", badge.isPresent(),
                "badgeScore", badge.map(UserSubjectBadge::getScore).orElse(0),
                "badgeTotal", badge.map(UserSubjectBadge::getTotal).orElse(0),
                "conceptCount", concepts.size()
        );
    }

    public Map<String, Object> getRoadmapStatus(String roadmapId, String userId) {
        List<RoadmapSubject> roadmapSubjects = roadmapSubjectRepo.findByRoadmapIdOrderByOrderIndex(roadmapId);
        boolean allSubjectsDone = !roadmapSubjects.isEmpty() && roadmapSubjects.stream()
                .allMatch(rs -> badgeRepo.existsByUserIdAndSubjectId(userId, rs.getSubjectId()));
        java.util.Optional<UserRoadmapBadge> rb = roadmapBadgeRepo.findByUserIdAndRoadmapId(userId, roadmapId);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("allSubjectsDone", allSubjectsDone);
        result.put("hasBadge",  rb.isPresent());
        result.put("badge",     rb.map(UserRoadmapBadge::getBadge).orElse(null));
        result.put("badgeScore", rb.map(UserRoadmapBadge::getScore).orElse(0));
        result.put("badgeTotal", rb.map(UserRoadmapBadge::getTotal).orElse(0));
        return result;
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private void checkCooldown(String type, String refId, String userId) {
        Optional<QuizAttempt> latest = attemptRepo
                .findTopByUserIdAndTypeAndRefIdOrderByTakenAtDesc(userId, type, refId);
        if (latest.isPresent() && !latest.get().isPassed() && latest.get().getNextRetryAt() != null) {
            if (LocalDateTime.now().isBefore(latest.get().getNextRetryAt())) {
                throw new RuntimeException("Please wait before retrying. Next attempt available at: "
                        + latest.get().getNextRetryAt());
            }
        }
    }
}
