package com.example.student.service;

import com.example.student.dto.AptitudeCategoryDTO;
import com.example.student.dto.AptitudeGroupDTO;
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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private final AptitudeTopicRepository topicRepo;
    private final LogicalTopicRepository logicalRepo;
    private final VerbalTopicRepository verbalRepo;
    private final AptitudeGroupRepository groupRepo;
    private final AptitudeQuestionRepository questionRepo;
    private final CacheService cacheService;

    public AptitudeService(AptitudeTopicRepository topicRepo,
                           LogicalTopicRepository logicalRepo,
                           VerbalTopicRepository verbalRepo,
                           AptitudeGroupRepository groupRepo,
                           AptitudeQuestionRepository questionRepo,
                           CacheService cacheService) {
        this.topicRepo = topicRepo;
        this.logicalRepo = logicalRepo;
        this.verbalRepo = verbalRepo;
        this.groupRepo = groupRepo;
        this.questionRepo = questionRepo;
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
