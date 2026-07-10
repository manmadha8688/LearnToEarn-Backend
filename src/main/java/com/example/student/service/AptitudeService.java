package com.example.student.service;

import com.example.student.dto.AptitudeCategoryDTO;
import com.example.student.model.AptitudeTopic;
import com.example.student.repository.AptitudeTopicRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AptitudeService {

    // Fixed category order — mirrors the static category cards in the frontend.
    public static final List<String> CATEGORIES = List.of(
            "quantitative", "logical", "verbal", "data-interpretation");

    private final AptitudeTopicRepository repo;
    private final CacheService cacheService;

    public AptitudeService(AptitudeTopicRepository repo, CacheService cacheService) {
        this.repo = repo;
        this.cacheService = cacheService;
    }

    /** All 4 categories with their live topic counts. */
    public List<AptitudeCategoryDTO> getCategories() {
        return cacheService.get("aptitude", "categories", () -> {
            List<AptitudeTopic> all = repo.findActiveLight();
            List<AptitudeCategoryDTO> out = new ArrayList<>();
            for (String cat : CATEGORIES) {
                long count = all.stream().filter(t -> cat.equals(t.getCategory())).count();
                out.add(AptitudeCategoryDTO.builder().category(cat).topicCount(count).build());
            }
            return out;
        });
    }

    /** All active topics in a category, ordered (lesson blocks excluded). */
    public List<AptitudeTopic> getTopics(String category) {
        return cacheService.get("aptitude", "cat:" + category,
                () -> repo.findCategoryLight(category));
    }

    /** A single topic by its slug. */
    public AptitudeTopic getTopic(String topicId) {
        return cacheService.get("aptitude", "topic:" + topicId,
                () -> repo.findByTopic(topicId).orElse(null));
    }
}
