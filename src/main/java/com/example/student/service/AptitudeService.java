package com.example.student.service;

import com.example.student.config.QuizConstants;
import com.example.student.dto.AptitudeCategoryDTO;
import com.example.student.dto.AptitudeGroupDTO;
import com.example.student.dto.AptitudeMockPaperDTO;
import com.example.student.dto.AptitudeMockQuestionDTO;
import com.example.student.dto.AptitudeMockResultDTO;
import com.example.student.dto.AptitudeMockReviewItemDTO;
import com.example.student.dto.AptitudeMockSectionDTO;
import com.example.student.dto.AptitudeMockSectionResultDTO;
import com.example.student.dto.AptitudeMockSubmitDTO;
import com.example.student.dto.QuizAttemptResponse;
import com.example.student.model.AptitudeGroup;
import com.example.student.model.AptitudeQuestion;
import com.example.student.model.AptitudeTopic;
import com.example.student.model.LogicalTopic;
import com.example.student.model.QuizAttempt;
import com.example.student.model.VerbalTopic;
import com.example.student.repository.AptitudeGroupRepository;
import com.example.student.repository.AptitudeQuestionRepository;
import com.example.student.repository.AptitudeTopicRepository;
import com.example.student.repository.LogicalTopicRepository;
import com.example.student.repository.QuizAttemptRepository;
import com.example.student.repository.VerbalTopicRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Read-only aptitude data service.
 *
 * Topics live in three collections because the categories need different lesson
 * shapes (quantitative is formula-based; logical is pattern-based; verbal is
 * rule-based). Navigation (categories → groups → topics) is unified here so the
 * frontend and API stay identical regardless of which collection a topic is in:
 *   - quantitative + data-interpretation → {@code aptitude_topics}
 *   - logical                            → {@code logical_topics}
 *   - verbal                             → {@code verbal_topics}
 */
@Service
public class AptitudeService {

    // Fixed category order — mirrors the static category cards in the frontend.
    public static final List<String> CATEGORIES = List.of(
            "quantitative", "logical", "verbal", "data-interpretation");

    /** Core mock exam — quant + logical + verbal only (no DI). */
    public static final int MOCK_OVERALL_SECONDS = 65 * 60;
    public static final int MOCK_QUANT_SECONDS = 25 * 60;
    public static final int MOCK_LOGICAL_SECONDS = 20 * 60;
    public static final int MOCK_VERBAL_SECONDS = 20 * 60;
    public static final int MOCK_QUANT_COUNT = 20;
    public static final int MOCK_LOGICAL_COUNT = 15;
    public static final int MOCK_VERBAL_COUNT = 15;
    public static final String MOCK_REF_ID = "core";

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final List<MockSectionSpec> MOCK_SECTIONS = List.of(
            new MockSectionSpec("quantitative", "Quantitative Aptitude", "🧮", "#0EA5E9",
                    MOCK_QUANT_SECONDS, MOCK_QUANT_COUNT),
            new MockSectionSpec("logical", "Logical Reasoning", "🧩", "#9B6ED4",
                    MOCK_LOGICAL_SECONDS, MOCK_LOGICAL_COUNT),
            new MockSectionSpec("verbal", "Verbal Ability", "📖", "#22C55E",
                    MOCK_VERBAL_SECONDS, MOCK_VERBAL_COUNT)
    );

    private record MockSectionSpec(String id, String label, String icon, String color,
                                   int timeSeconds, int questionCount) {}

    private final AptitudeTopicRepository topicRepo;
    private final LogicalTopicRepository logicalRepo;
    private final VerbalTopicRepository verbalRepo;
    private final AptitudeGroupRepository groupRepo;
    private final AptitudeQuestionRepository questionRepo;
    private final QuizAttemptRepository attemptRepo;
    private final ProgressService progressService;
    private final CacheService cacheService;

    public AptitudeService(AptitudeTopicRepository topicRepo,
                           LogicalTopicRepository logicalRepo,
                           VerbalTopicRepository verbalRepo,
                           AptitudeGroupRepository groupRepo,
                           AptitudeQuestionRepository questionRepo,
                           QuizAttemptRepository attemptRepo,
                           ProgressService progressService,
                           CacheService cacheService) {
        this.topicRepo = topicRepo;
        this.logicalRepo = logicalRepo;
        this.verbalRepo = verbalRepo;
        this.groupRepo = groupRepo;
        this.questionRepo = questionRepo;
        this.attemptRepo = attemptRepo;
        this.progressService = progressService;
        this.cacheService = cacheService;
    }

    /** All 4 categories with their live group + topic counts. */
    public List<AptitudeCategoryDTO> getCategories() {
        return cacheService.get("aptitude", "categories", () -> {
            List<AptitudeGroup> allGroups = groupRepo.findAll();
            // Topic counts per category from whichever collection owns them.
            Map<String, Long> topicCounts = new HashMap<>();
            for (AptitudeTopic t : topicRepo.findActiveLight()) {
                topicCounts.merge(t.getCategory(), 1L, Long::sum);
            }
            topicCounts.put("logical", (long) logicalRepo.findActiveLight().size());
            topicCounts.put("verbal", (long) verbalRepo.findActiveLight().size());

            List<AptitudeCategoryDTO> out = new ArrayList<>();
            for (String cat : CATEGORIES) {
                long groups = allGroups.stream().filter(g -> cat.equals(g.getCategory()) && g.isActive()).count();
                out.add(AptitudeCategoryDTO.builder()
                        .category(cat)
                        .groupCount(groups)
                        .topicCount(topicCounts.getOrDefault(cat, 0L))
                        .build());
            }
            return out;
        });
    }

    /** All active groups in a category, each with its live topic count + topic names. */
    public List<AptitudeGroupDTO> getGroups(String category) {
        return cacheService.get("aptitude", "groups:" + category, () -> {
            List<AptitudeGroup> groups = groupRepo.findByCategoryAndIsActiveTrueOrderByOrderAsc(category);
            Map<String, List<String>> topicNames = topicNamesByGroup(category);
            List<AptitudeGroupDTO> out = new ArrayList<>();
            for (AptitudeGroup g : groups) {
                List<String> names = topicNames.getOrDefault(g.getSlug(), List.of());
                out.add(AptitudeGroupDTO.builder()
                        .slug(g.getSlug())
                        .category(g.getCategory())
                        .displayName(g.getDisplayName())
                        .description(g.getDescription())
                        .icon(g.getIcon())
                        .order(g.getOrder())
                        .topicCount(names.size())
                        .topicNames(names)
                        .build());
            }
            return out;
        });
    }

    /** All active topics in a group, ordered (lesson blocks excluded). */
    public List<?> getTopics(String group) {
        return cacheService.get("aptitude", "group:" + group, () -> {
            String category = groupRepo.findBySlug(group)
                    .map(AptitudeGroup::getCategory).orElse(null);
            if ("logical".equals(category)) return logicalRepo.findGroupLight(group);
            if ("verbal".equals(category))  return verbalRepo.findGroupLight(group);
            return topicRepo.findGroupLight(group);
        });
    }

    /**
     * A single topic by its slug. Slugs are globally unique, so we resolve which
     * collection holds it. Returns the full lesson object of the matching type.
     */
    public Object getTopic(String topicId) {
        return cacheService.get("aptitude", "topic:" + topicId, () -> {
            // Inactive topics are "hidden": invisible in navigation AND unreachable
            // by direct URL, so the admin Active toggle actually takes them offline.
            AptitudeTopic quant = topicRepo.findByTopic(topicId).orElse(null);
            if (quant != null) return quant.isActive() ? quant : null;
            LogicalTopic logical = logicalRepo.findByTopic(topicId).orElse(null);
            if (logical != null) return logical.isActive() ? logical : null;
            VerbalTopic verbal = verbalRepo.findByTopic(topicId).orElse(null);
            return (verbal != null && verbal.isActive()) ? verbal : null;
        });
    }

    /** All practice questions for a topic, in order. */
    public List<AptitudeQuestion> getQuestions(String topicId) {
        return cacheService.get("aptitude", "questions:" + topicId,
                () -> questionRepo.findByTopicAndIsActiveTrueOrderByOrderAsc(topicId));
    }

    /**
     * Pre-warm every aptitude cache entry on startup: categories → per-category
     * groups → per-group topic lists → each active topic's full lesson + its
     * questions, across all three collections. Uses the same public read methods
     * (and therefore the same cache keys) the API serves, so the first request
     * after a restart/redeploy is served from cache instead of MongoDB.
     *
     * @return the number of topics warmed (across quantitative/DI, logical, verbal)
     */
    public int warmAll() {
        getCategories();
        for (String cat : CATEGORIES) getGroups(cat);

        // Per-group topic lists (group:{slug})
        for (AptitudeGroup g : groupRepo.findAll()) {
            if (g.isActive()) getTopics(g.getSlug());
        }

        // Every active topic's full lesson (topic:{slug}) + its questions (questions:{slug})
        int topics = 0;
        for (AptitudeTopic t : topicRepo.findActiveLight()) {
            getTopic(t.getTopic());
            getQuestions(t.getTopic());
            topics++;
        }
        for (LogicalTopic t : logicalRepo.findActiveLight()) {
            getTopic(t.getTopic());
            getQuestions(t.getTopic());
            topics++;
        }
        for (VerbalTopic t : verbalRepo.findActiveLight()) {
            getTopic(t.getTopic());
            getQuestions(t.getTopic());
            topics++;
        }
        return topics;
    }

    /** Random 50-question core mock (20 quant + 15 logical + 15 verbal). */
    public AptitudeMockPaperDTO buildMockPaper(String userId) {
        checkMockCanStart(userId);
        return buildMockPaperInternal();
    }

    private AptitudeMockPaperDTO buildMockPaperInternal() {
        List<AptitudeMockSectionDTO> sections = new ArrayList<>();
        int total = 0;
        for (MockSectionSpec spec : MOCK_SECTIONS) {
            List<AptitudeQuestion> picked = sampleQuestions(spec.id(), spec.questionCount());
            List<AptitudeMockQuestionDTO> questions = new ArrayList<>();
            for (int i = 0; i < picked.size(); i++) {
                questions.add(toMockQuestion(picked.get(i), i + 1));
            }
            sections.add(AptitudeMockSectionDTO.builder()
                    .id(spec.id())
                    .label(spec.label())
                    .icon(spec.icon())
                    .color(spec.color())
                    .timeSeconds(spec.timeSeconds())
                    .questionCount(questions.size())
                    .questions(questions)
                    .build());
            total += questions.size();
        }
        return AptitudeMockPaperDTO.builder()
                .overallTimeSeconds(MOCK_OVERALL_SECONDS)
                .totalQuestions(total)
                .sections(sections)
                .build();
    }

    /** Grade a submitted mock, persist summary attempt, award XP delta on pass. Full review in response only. */
    @Transactional
    public AptitudeMockResultDTO submitMock(AptitudeMockSubmitDTO submit, String userId) {
        Map<String, String> answers = submit.getAnswers() != null ? submit.getAnswers() : Map.of();
        List<String> orderedIds = submit.getQuestionIds() != null && !submit.getQuestionIds().isEmpty()
                ? submit.getQuestionIds()
                : new ArrayList<>(answers.keySet());

        Map<String, AptitudeQuestion> byId = new HashMap<>();
        questionRepo.findAllById(orderedIds).forEach(q -> byId.put(q.getId(), q));

        List<AptitudeMockSectionResultDTO> sectionResults = new ArrayList<>();
        int correctTotal = 0;
        int questionTotal = 0;

        for (MockSectionSpec spec : MOCK_SECTIONS) {
            List<AptitudeMockReviewItemDTO> items = new ArrayList<>();
            int sectionCorrect = 0;
            int order = 1;

            for (String id : orderedIds) {
                AptitudeQuestion q = byId.get(id);
                if (q == null || !spec.id().equals(q.getCategory())) continue;

                String chosen = normalizeLetter(answers.get(id));
                String correct = normalizeLetter(q.getAnswer());
                boolean ok = chosen != null && chosen.equals(correct);
                if (ok) sectionCorrect++;

                items.add(AptitudeMockReviewItemDTO.builder()
                        .id(q.getId())
                        .order(order++)
                        .question(q.getQuestion())
                        .options(q.getOptions())
                        .correctAnswer(correct)
                        .chosenAnswer(chosen)
                        .correct(ok)
                        .solution(q.getSolution())
                        .trick(q.getTrick())
                        .build());
            }

            questionTotal += items.size();
            correctTotal += sectionCorrect;
            sectionResults.add(AptitudeMockSectionResultDTO.builder()
                    .id(spec.id())
                    .label(spec.label())
                    .color(spec.color())
                    .correct(sectionCorrect)
                    .total(items.size())
                    .items(items)
                    .build());
        }

        int pct = questionTotal > 0 ? (int) Math.round(correctTotal * 100.0 / questionTotal) : 0;
        boolean passed = correctTotal >= QuizConstants.MOCK_PASS;

        int previousBest = attemptRepo.findByUserIdAndTypeAndRefId(userId, "APTITUDE_MOCK", MOCK_REF_ID)
                .stream().mapToInt(QuizAttempt::getScore).max().orElse(0);

        int xpEarned = 0;
        if (passed) {
            xpEarned = progressService.awardXp(userId,
                    QuizConstants.mockQuizXpDelta(correctTotal, previousBest));
        }

        LocalDateTime nextRetryAt = LocalDateTime.now(IST).plusMinutes(QuizConstants.MOCK_RETRY_MINUTES);

        QuizAttempt attempt = new QuizAttempt();
        attempt.setUserId(userId);
        attempt.setType("APTITUDE_MOCK");
        attempt.setRefId(MOCK_REF_ID);
        attempt.setScore(correctTotal);
        attempt.setTotal(questionTotal);
        attempt.setPassed(passed);
        attempt.setTakenAt(LocalDateTime.now(IST));
        attempt.setNextRetryAt(nextRetryAt);
        attempt.setXpEarned(xpEarned);
        attempt.setDailyBonusEarned(false);
        QuizAttempt saved = attemptRepo.save(attempt);

        cacheService.evict("quizHistory", userId + ":5");
        cacheService.evict("aptitudeMockStatus", userId);
        cacheService.evict("aptitudeMockHistory", userId + ":10");
        if (xpEarned > 0) cacheService.evict("hunterStats", userId);

        return AptitudeMockResultDTO.builder()
                .attemptId(saved.getId())
                .correct(correctTotal)
                .total(questionTotal)
                .percentage(pct)
                .passMark(QuizConstants.MOCK_PASS)
                .passed(passed)
                .xpEarned(xpEarned)
                .nextRetryAt(nextRetryAt)
                .sections(sectionResults)
                .build();
    }

    /** Mock retry gate + best score summary for the aptitude page. */
    public QuizAttemptResponse getMockStatus(String userId) {
        return cacheService.get("aptitudeMockStatus", userId, () -> buildMockStatus(userId));
    }

    /** Recent mock attempts (summary only — newest first). */
    public List<Map<String, Object>> getMockHistory(String userId, int limit) {
        int effectiveLimit = limit <= 0 ? 10 : Math.min(limit, 50);
        return cacheService.get("aptitudeMockHistory", userId + ":" + effectiveLimit, () -> {
            List<QuizAttempt> attempts = attemptRepo.findByUserIdAndTypeAndRefId(
                    userId, "APTITUDE_MOCK", MOCK_REF_ID);
            attempts.sort(Comparator.comparing(QuizAttempt::getTakenAt,
                    Comparator.nullsLast(Comparator.reverseOrder())));

            List<Map<String, Object>> out = new ArrayList<>();
            for (QuizAttempt a : attempts) {
                int pct = a.getTotal() > 0 ? (int) Math.round(a.getScore() * 100.0 / a.getTotal()) : 0;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", a.getId());
                m.put("score", a.getScore());
                m.put("total", a.getTotal());
                m.put("scorePercent", pct);
                m.put("passed", a.isPassed());
                m.put("xpEarned", a.getXpEarned());
                m.put("takenAt", a.getTakenAt() != null ? a.getTakenAt().toString() : null);
                out.add(m);
                if (out.size() >= effectiveLimit) break;
            }
            return out;
        });
    }

    private QuizAttemptResponse buildMockStatus(String userId) {
        List<QuizAttempt> all = attemptRepo.findByUserIdAndTypeAndRefId(
                userId, "APTITUDE_MOCK", MOCK_REF_ID);
        if (all.isEmpty()) {
            return new QuizAttemptResponse(true, false, 0, QuizConstants.MOCK_TOTAL, 0, null, null);
        }

        Optional<QuizAttempt> best = all.stream().max(Comparator.comparingInt(QuizAttempt::getScore));
        Optional<QuizAttempt> latest = all.stream().max(Comparator.comparing(QuizAttempt::getTakenAt));
        boolean hasPassed = all.stream().anyMatch(QuizAttempt::isPassed);

        boolean canRetry = true;
        LocalDateTime nextRetryAt = null;
        if (latest.isPresent() && latest.get().getNextRetryAt() != null) {
            nextRetryAt = latest.get().getNextRetryAt();
            if (LocalDateTime.now(IST).isBefore(nextRetryAt)) canRetry = false;
        }

        return new QuizAttemptResponse(
                canRetry,
                hasPassed,
                best.map(QuizAttempt::getScore).orElse(0),
                best.map(QuizAttempt::getTotal).orElse(QuizConstants.MOCK_TOTAL),
                all.size(),
                nextRetryAt,
                latest.map(QuizAttempt::getTakenAt).orElse(null)
        );
    }

    private void checkMockCanStart(String userId) {
        Optional<QuizAttempt> latest = attemptRepo.findTopByUserIdAndTypeAndRefIdOrderByTakenAtDesc(
                userId, "APTITUDE_MOCK", MOCK_REF_ID);
        if (latest.isPresent() && latest.get().getNextRetryAt() != null
                && LocalDateTime.now(IST).isBefore(latest.get().getNextRetryAt())) {
            throw new RuntimeException("Please wait before retrying. Next attempt available at: "
                    + latest.get().getNextRetryAt());
        }
    }

    private List<AptitudeQuestion> sampleQuestions(String category, int count) {
        List<AptitudeQuestion> pool = new ArrayList<>(questionRepo.findByCategoryAndIsActiveTrue(category));
        Collections.shuffle(pool, ThreadLocalRandom.current());
        return pool.stream().limit(count).collect(Collectors.toList());
    }

    private static AptitudeMockQuestionDTO toMockQuestion(AptitudeQuestion q, int displayOrder) {
        return AptitudeMockQuestionDTO.builder()
                .id(q.getId())
                .order(displayOrder)
                .question(q.getQuestion())
                .options(q.getOptions())
                .difficulty(q.getDifficulty())
                .build();
    }

    private static String normalizeLetter(String letter) {
        if (letter == null || letter.isBlank()) return null;
        return letter.trim().toUpperCase().substring(0, 1);
    }

    /**
     * group-slug → ordered active-topic display names, from the collection that
     * owns the category. Topic count is derived from the list size, so the group
     * card can also be searched by the topics it contains.
     */
    private Map<String, List<String>> topicNamesByGroup(String category) {
        Map<String, List<String>> names = new LinkedHashMap<>();
        if ("logical".equals(category)) {
            for (LogicalTopic t : logicalRepo.findActiveLight())
                names.computeIfAbsent(t.getGroup(), k -> new ArrayList<>()).add(t.getDisplayName());
        } else if ("verbal".equals(category)) {
            for (VerbalTopic t : verbalRepo.findActiveLight())
                names.computeIfAbsent(t.getGroup(), k -> new ArrayList<>()).add(t.getDisplayName());
        } else {
            for (AptitudeTopic t : topicRepo.findCategoryLight(category))
                names.computeIfAbsent(t.getGroup(), k -> new ArrayList<>()).add(t.getDisplayName());
        }
        return names;
    }
}
