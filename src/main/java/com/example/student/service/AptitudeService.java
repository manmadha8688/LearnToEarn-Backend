package com.example.student.service;

import com.example.student.dto.AptitudeCategoryDTO;
import com.example.student.dto.AptitudeGroupDTO;
import com.example.student.model.AptitudeGroup;
import com.example.student.model.AptitudeQuestion;
import com.example.student.model.AptitudeTopic;
import com.example.student.repository.AptitudeGroupRepository;
import com.example.student.repository.AptitudeQuestionRepository;
import com.example.student.repository.AptitudeTopicRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AptitudeService {

    // Fixed category order — mirrors the static category cards in the frontend.
    public static final List<String> CATEGORIES = List.of(
            "quantitative", "logical", "verbal", "data-interpretation");

    private final AptitudeTopicRepository topicRepo;
    private final AptitudeGroupRepository groupRepo;
    private final AptitudeQuestionRepository questionRepo;
    private final CacheService cacheService;

    public AptitudeService(AptitudeTopicRepository topicRepo,
                           AptitudeGroupRepository groupRepo,
                           AptitudeQuestionRepository questionRepo,
                           CacheService cacheService) {
        this.topicRepo = topicRepo;
        this.groupRepo = groupRepo;
        this.questionRepo = questionRepo;
        this.cacheService = cacheService;
    }

    /** All 4 categories with their live group + topic counts. */
    public List<AptitudeCategoryDTO> getCategories() {
        return cacheService.get("aptitude", "categories", () -> {
            List<AptitudeTopic> allTopics = topicRepo.findActiveLight();
            List<AptitudeGroup> allGroups = groupRepo.findAll();
            List<AptitudeCategoryDTO> out = new ArrayList<>();
            for (String cat : CATEGORIES) {
                long topics = allTopics.stream().filter(t -> cat.equals(t.getCategory())).count();
                long groups = allGroups.stream().filter(g -> cat.equals(g.getCategory()) && g.isActive()).count();
                out.add(AptitudeCategoryDTO.builder()
                        .category(cat).groupCount(groups).topicCount(topics).build());
            }
            return out;
        });
    }

    /** All active groups in a category, each with its live topic count. */
    public List<AptitudeGroupDTO> getGroups(String category) {
        return cacheService.get("aptitude", "groups:" + category, () -> {
            List<AptitudeGroup> groups = groupRepo.findByCategoryAndIsActiveTrueOrderByOrderAsc(category);
            List<AptitudeTopic> topics = topicRepo.findCategoryLight(category);
            List<AptitudeGroupDTO> out = new ArrayList<>();
            for (AptitudeGroup g : groups) {
                long count = topics.stream().filter(t -> g.getSlug().equals(t.getGroup())).count();
                out.add(AptitudeGroupDTO.builder()
                        .slug(g.getSlug())
                        .category(g.getCategory())
                        .displayName(g.getDisplayName())
                        .description(g.getDescription())
                        .icon(g.getIcon())
                        .order(g.getOrder())
                        .topicCount(count)
                        .build());
            }
            return out;
        });
    }

    /** All active topics in a group, ordered (lesson blocks excluded). */
    public List<AptitudeTopic> getTopics(String group) {
        return cacheService.get("aptitude", "group:" + group,
                () -> topicRepo.findGroupLight(group));
    }

    /** A single topic by its slug. */
    public AptitudeTopic getTopic(String topicId) {
        return cacheService.get("aptitude", "topic:" + topicId,
                () -> topicRepo.findByTopic(topicId).orElse(null));
    }

    /** All practice questions for a topic, in order. */
    public List<AptitudeQuestion> getQuestions(String topicId) {
        return cacheService.get("aptitude", "questions:" + topicId,
                () -> questionRepo.findByTopicAndIsActiveTrueOrderByOrderAsc(topicId));
    }
}
