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

    /** All active groups in a category, each with its live topic count. */
    public List<AptitudeGroupDTO> getGroups(String category) {
        return cacheService.get("aptitude", "groups:" + category, () -> {
            List<AptitudeGroup> groups = groupRepo.findByCategoryAndIsActiveTrueOrderByOrderAsc(category);
            Map<String, Long> counts = topicCountsByGroup(category);
            List<AptitudeGroupDTO> out = new ArrayList<>();
            for (AptitudeGroup g : groups) {
                out.add(AptitudeGroupDTO.builder()
                        .slug(g.getSlug())
                        .category(g.getCategory())
                        .displayName(g.getDisplayName())
                        .description(g.getDescription())
                        .icon(g.getIcon())
                        .order(g.getOrder())
                        .topicCount(counts.getOrDefault(g.getSlug(), 0L))
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
            AptitudeTopic quant = topicRepo.findByTopic(topicId).orElse(null);
            if (quant != null) return quant;
            LogicalTopic logical = logicalRepo.findByTopic(topicId).orElse(null);
            if (logical != null) return logical;
            return verbalRepo.findByTopic(topicId).orElse(null);
        });
    }

    /** All practice questions for a topic, in order. */
    public List<AptitudeQuestion> getQuestions(String topicId) {
        return cacheService.get("aptitude", "questions:" + topicId,
                () -> questionRepo.findByTopicAndIsActiveTrueOrderByOrderAsc(topicId));
    }

    /** group-slug → active-topic count, reading from the collection that owns the category. */
    private Map<String, Long> topicCountsByGroup(String category) {
        Map<String, Long> counts = new HashMap<>();
        if ("logical".equals(category)) {
            for (LogicalTopic t : logicalRepo.findActiveLight()) counts.merge(t.getGroup(), 1L, Long::sum);
        } else if ("verbal".equals(category)) {
            for (VerbalTopic t : verbalRepo.findActiveLight()) counts.merge(t.getGroup(), 1L, Long::sum);
        } else {
            for (AptitudeTopic t : topicRepo.findCategoryLight(category)) counts.merge(t.getGroup(), 1L, Long::sum);
        }
        return counts;
    }
}
