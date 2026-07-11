package com.example.student.service;

import com.example.student.exception.ResourceNotFoundException;
import com.example.student.model.AptitudeGroup;
import com.example.student.model.AptitudeQuestion;
import com.example.student.model.AptitudeTopic;
import com.example.student.model.LogicalTopic;
import com.example.student.model.VerbalTopic;
import com.example.student.repository.AptitudeGroupRepository;
import com.example.student.repository.AptitudeQuestionRepository;
import com.example.student.repository.AptitudeTopicRepository;
import com.example.student.repository.LogicalTopicRepository;
import com.example.student.repository.VerbalTopicRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Admin CRUD for the aptitude feature.
 *
 * Topics live in three collections (quantitative + data-interpretation →
 * {@code aptitude_topics}, logical → {@code logical_topics}, verbal →
 * {@code verbal_topics}) because each category needs a different lesson shape.
 * This service hides that split behind category-aware dispatch so the admin
 * panel can treat "a topic" uniformly.
 *
 * Every mutation evicts the whole {@code "aptitude"} cache so student reads
 * (categories, groups, topics, questions — cached for 24h) reflect edits at once.
 */
@Service
public class AptitudeAdminService {

    private final AptitudeGroupRepository groupRepo;
    private final AptitudeTopicRepository topicRepo;
    private final LogicalTopicRepository logicalRepo;
    private final VerbalTopicRepository verbalRepo;
    private final AptitudeQuestionRepository questionRepo;
    private final CacheService cacheService;

    // Lenient mapper: topic bodies carry category-specific nested lesson objects,
    // and we never want an unexpected/extra field to blow up an admin save.
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public AptitudeAdminService(AptitudeGroupRepository groupRepo,
                                AptitudeTopicRepository topicRepo,
                                LogicalTopicRepository logicalRepo,
                                VerbalTopicRepository verbalRepo,
                                AptitudeQuestionRepository questionRepo,
                                CacheService cacheService) {
        this.groupRepo = groupRepo;
        this.topicRepo = topicRepo;
        this.logicalRepo = logicalRepo;
        this.verbalRepo = verbalRepo;
        this.questionRepo = questionRepo;
        this.cacheService = cacheService;
    }

    private void evict() {
        cacheService.evictAll("aptitude");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private boolean isLogical(String category) { return "logical".equals(category); }
    private boolean isVerbal(String category)  { return "verbal".equals(category); }

    // ── GROUPS ───────────────────────────────────────────────────────────────

    public List<AptitudeGroup> listGroups() {
        return groupRepo.findAllByOrderByCategoryAscOrderAsc();
    }

    public AptitudeGroup createGroup(AptitudeGroup g) {
        require(!isBlank(g.getCategory()), "category is required");
        require(!isBlank(g.getSlug()), "slug is required");
        require(!isBlank(g.getDisplayName()), "displayName is required");
        require(AptitudeService.CATEGORIES.contains(g.getCategory()), "invalid category");
        if (groupRepo.existsBySlug(g.getSlug()))
            throw new IllegalArgumentException("A group with slug '" + g.getSlug() + "' already exists");
        g.setId(null);
        AptitudeGroup saved = groupRepo.save(g);
        evict();
        return saved;
    }

    public AptitudeGroup updateGroup(String id, AptitudeGroup body) {
        AptitudeGroup existing = groupRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        if (!isBlank(body.getSlug()) && !body.getSlug().equals(existing.getSlug())
                && groupRepo.existsBySlug(body.getSlug()))
            throw new IllegalArgumentException("A group with slug '" + body.getSlug() + "' already exists");

        if (!isBlank(body.getCategory()))    existing.setCategory(body.getCategory());
        if (!isBlank(body.getSlug()))        existing.setSlug(body.getSlug());
        if (!isBlank(body.getDisplayName())) existing.setDisplayName(body.getDisplayName());
        existing.setDescription(body.getDescription());
        existing.setIcon(body.getIcon());
        existing.setOrder(body.getOrder());
        existing.setActive(body.isActive());
        AptitudeGroup saved = groupRepo.save(existing);
        evict();
        return saved;
    }

    public void deleteGroup(String id) {
        AptitudeGroup g = groupRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        // Block instead of orphaning: a group's topics would otherwise become
        // unreachable in navigation while lingering in the DB.
        if (groupHasTopics(g.getCategory(), g.getSlug()))
            throw new IllegalArgumentException(
                    "This group still has topics. Delete or move its topics first.");
        groupRepo.delete(g);
        evict();
    }

    private boolean groupHasTopics(String category, String slug) {
        if (isLogical(category)) return !logicalRepo.findGroupAdmin(slug).isEmpty();
        if (isVerbal(category))  return !verbalRepo.findGroupAdmin(slug).isEmpty();
        return !topicRepo.findGroupAdmin(slug).isEmpty();
    }

    // ── TOPICS ─────────────────────────────────────────────────────────────

    /** Admin list of a group's topics (incl. inactive; no heavy lesson block). */
    public List<?> listTopics(String group) {
        String category = groupRepo.findBySlug(group)
                .map(AptitudeGroup::getCategory).orElse(null);
        if (isLogical(category)) return logicalRepo.findGroupAdmin(group);
        if (isVerbal(category))  return verbalRepo.findGroupAdmin(group);
        return topicRepo.findGroupAdmin(group);
    }

    /** Full topic (with lesson) by slug — for the edit form. */
    public Object getTopic(String topicId) {
        AptitudeTopic quant = topicRepo.findByTopic(topicId).orElse(null);
        if (quant != null) return quant;
        LogicalTopic logical = logicalRepo.findByTopic(topicId).orElse(null);
        if (logical != null) return logical;
        return verbalRepo.findByTopic(topicId).orElse(null);
    }

    public Object createTopic(Map<String, Object> body) {
        String category = (String) body.get("category");
        String slug = (String) body.get("topic");
        require(!isBlank(category), "category is required");
        require(!isBlank(slug), "topic (slug) is required");
        require(!isBlank((String) body.get("group")), "group is required");
        require(AptitudeService.CATEGORIES.contains(category), "invalid category");
        if (slugTaken(slug))
            throw new IllegalArgumentException("A topic with slug '" + slug + "' already exists");

        body.remove("id"); // let Mongo assign
        if (isLogical(category)) {
            LogicalTopic t = mapper.convertValue(body, LogicalTopic.class);
            t.setId(null);
            LogicalTopic saved = logicalRepo.save(t);
            evict();
            return saved;
        }
        if (isVerbal(category)) {
            VerbalTopic t = mapper.convertValue(body, VerbalTopic.class);
            t.setId(null);
            VerbalTopic saved = verbalRepo.save(t);
            evict();
            return saved;
        }
        AptitudeTopic t = mapper.convertValue(body, AptitudeTopic.class);
        t.setId(null);
        AptitudeTopic saved = topicRepo.save(t);
        evict();
        return saved;
    }

    public Object updateTopic(String id, Map<String, Object> body) {
        String category = (String) body.get("category");
        require(!isBlank(category), "category is required");
        require(AptitudeService.CATEGORIES.contains(category), "invalid category");

        Object result;
        if (isLogical(category)) {
            LogicalTopic existing = logicalRepo.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Topic not found"));
            LogicalTopic t = mapper.convertValue(body, LogicalTopic.class);
            t.setId(existing.getId());
            result = logicalRepo.save(t);
        } else if (isVerbal(category)) {
            VerbalTopic existing = verbalRepo.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Topic not found"));
            VerbalTopic t = mapper.convertValue(body, VerbalTopic.class);
            t.setId(existing.getId());
            result = verbalRepo.save(t);
        } else {
            AptitudeTopic existing = topicRepo.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Topic not found"));
            AptitudeTopic t = mapper.convertValue(body, AptitudeTopic.class);
            t.setId(existing.getId());
            result = topicRepo.save(t);
        }
        evict();
        return result;
    }

    /** Delete a topic (found by id across all three collections) and its questions. */
    @Transactional
    public void deleteTopic(String id) {
        AptitudeTopic quant = topicRepo.findById(id).orElse(null);
        if (quant != null) {
            deleteQuestionsForTopic(quant.getTopic());
            topicRepo.delete(quant);
            evict();
            return;
        }
        LogicalTopic logical = logicalRepo.findById(id).orElse(null);
        if (logical != null) {
            deleteQuestionsForTopic(logical.getTopic());
            logicalRepo.delete(logical);
            evict();
            return;
        }
        VerbalTopic verbal = verbalRepo.findById(id).orElse(null);
        if (verbal != null) {
            deleteQuestionsForTopic(verbal.getTopic());
            verbalRepo.delete(verbal);
            evict();
            return;
        }
        throw new ResourceNotFoundException("Topic not found");
    }

    private void deleteQuestionsForTopic(String topicSlug) {
        List<AptitudeQuestion> qs = questionRepo.findByTopicOrderByOrderAsc(topicSlug);
        if (!qs.isEmpty()) questionRepo.deleteAll(qs);
    }

    /** True if the slug is already used by a topic in ANY of the three collections. */
    private boolean slugTaken(String slug) {
        return topicRepo.existsByTopic(slug)
                || logicalRepo.existsByTopic(slug)
                || verbalRepo.existsByTopic(slug);
    }

    /** Resolve a topic slug's category + group from whichever collection owns it. */
    private String[] categoryAndGroup(String topicSlug) {
        AptitudeTopic quant = topicRepo.findByTopic(topicSlug).orElse(null);
        if (quant != null) return new String[]{ quant.getCategory(), quant.getGroup() };
        LogicalTopic logical = logicalRepo.findByTopic(topicSlug).orElse(null);
        if (logical != null) return new String[]{ logical.getCategory(), logical.getGroup() };
        VerbalTopic verbal = verbalRepo.findByTopic(topicSlug).orElse(null);
        if (verbal != null) return new String[]{ verbal.getCategory(), verbal.getGroup() };
        return null;
    }

    // ── QUESTIONS ────────────────────────────────────────────────────────────

    public List<AptitudeQuestion> listQuestions(String topic) {
        return questionRepo.findByTopicOrderByOrderAsc(topic);
    }

    public AptitudeQuestion createQuestion(AptitudeQuestion q) {
        validateQuestion(q);
        q.setId(null); // let Mongo assign a unique id (avoid clobbering seeded {topic}-q{n})
        inheritCategoryGroup(q);
        AptitudeQuestion saved = questionRepo.save(q);
        evict();
        return saved;
    }

    public AptitudeQuestion updateQuestion(String id, AptitudeQuestion body) {
        AptitudeQuestion existing = questionRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));
        validateQuestion(body);
        body.setId(existing.getId());
        inheritCategoryGroup(body);
        AptitudeQuestion saved = questionRepo.save(body);
        evict();
        return saved;
    }

    public void deleteQuestion(String id) {
        AptitudeQuestion q = questionRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));
        questionRepo.delete(q);
        evict();
    }

    private void validateQuestion(AptitudeQuestion q) {
        require(!isBlank(q.getTopic()), "topic is required");
        require(!isBlank(q.getQuestion()), "question text is required");
        require(q.getOptions() != null && q.getOptions().size() == 4, "exactly 4 options are required");
        require(!isBlank(q.getAnswer()) && "ABCD".contains(q.getAnswer()),
                "answer must be one of A, B, C, D");
    }

    /** Keep a question's category/group aligned with its topic (mirrors the old seeder). */
    private void inheritCategoryGroup(AptitudeQuestion q) {
        String[] cg = categoryAndGroup(q.getTopic());
        if (cg != null) {
            q.setCategory(cg[0]);
            q.setGroup(cg[1]);
        }
    }
}
