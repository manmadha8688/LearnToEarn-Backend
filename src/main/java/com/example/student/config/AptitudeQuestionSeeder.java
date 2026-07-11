package com.example.student.config;

import com.example.student.model.AptitudeQuestion;
import com.example.student.model.AptitudeTopic;
import com.example.student.repository.AptitudeQuestionRepository;
import com.example.student.repository.AptitudeTopicRepository;
import com.example.student.service.CacheService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Seeds the {@code aptitude_questions} collection from
 * {@code resources/seed/aptitude-questions.json} on startup.
 *
 * Runs after {@link AptitudeSeeder} (@Order 1) so every question can inherit its
 * topic's category/group. Each question gets a deterministic id ({topic}-q{order})
 * making re-seeds idempotent, and stale questions are pruned.
 */
@Component
@Order(1)
public class AptitudeQuestionSeeder {

    private static final Logger log = LoggerFactory.getLogger(AptitudeQuestionSeeder.class);
    private static final String SEED_FILE = "seed/aptitude-questions.json";

    private final AptitudeQuestionRepository questionRepo;
    private final AptitudeTopicRepository topicRepo;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AptitudeQuestionSeeder(AptitudeQuestionRepository questionRepo,
                                  AptitudeTopicRepository topicRepo,
                                  CacheService cacheService) {
        this.questionRepo = questionRepo;
        this.topicRepo = topicRepo;
        this.cacheService = cacheService;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SeedQuestion {
        public String topic;
        public int order;
        public String difficulty;
        public String question;
        public List<String> options = new ArrayList<>();
        public String answer;
        public String solution;
        public String trick;
        public String type;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        try {
            List<SeedQuestion> defs = load();
            if (defs.isEmpty()) {
                log.info("Aptitude question seeder: no questions defined — skipping.");
                return;
            }

            int inserted = 0, updated = 0;
            Set<String> keep = new HashSet<>();
            Set<String> evictTopics = new HashSet<>();

            for (SeedQuestion d : defs) {
                String id = d.topic + "-q" + d.order;
                keep.add(id);

                // Inherit category/group from the topic (single source of truth).
                String category = null, group = null;
                Optional<AptitudeTopic> topicOpt = topicRepo.findByTopic(d.topic);
                if (topicOpt.isPresent()) {
                    category = topicOpt.get().getCategory();
                    group = topicOpt.get().getGroup();
                }

                AptitudeQuestion q = AptitudeQuestion.builder()
                        .id(id)
                        .topic(d.topic)
                        .category(category)
                        .group(group)
                        .order(d.order)
                        .difficulty(d.difficulty)
                        .question(d.question)
                        .options(d.options)
                        .answer(d.answer)
                        .solution(d.solution)
                        .trick(d.trick)
                        .type(d.type)
                        .isActive(true)
                        .build();

                Optional<AptitudeQuestion> existing = questionRepo.findById(id);
                if (existing.isEmpty() || !sameQuestion(existing.get(), q)) {
                    questionRepo.save(q);
                    evictTopics.add(d.topic);
                    if (existing.isEmpty()) inserted++; else updated++;
                }
            }

            // Prune questions no longer defined in the JSON.
            List<AptitudeQuestion> orphans = new ArrayList<>();
            for (AptitudeQuestion q : questionRepo.findAll()) {
                if (!keep.contains(q.getId())) { orphans.add(q); evictTopics.add(q.getTopic()); }
            }
            if (!orphans.isEmpty()) questionRepo.deleteAll(orphans);

            for (String t : evictTopics) cacheService.evict("aptitude", "questions:" + t);

            log.info("Aptitude question seeder: {} inserted, {} updated, {} pruned, {} defined.",
                    inserted, updated, orphans.size(), defs.size());
        } catch (Exception e) {
            log.error("Aptitude question seeder failed — app continues: {}", e.getMessage(), e);
        }
    }

    private static boolean sameQuestion(AptitudeQuestion a, AptitudeQuestion b) {
        return eq(a.getQuestion(), b.getQuestion())
                && eq(a.getAnswer(), b.getAnswer())
                && eq(a.getSolution(), b.getSolution())
                && eq(a.getTrick(), b.getTrick())
                && eq(a.getType(), b.getType())
                && eq(a.getDifficulty(), b.getDifficulty())
                && eq(a.getCategory(), b.getCategory())
                && eq(a.getGroup(), b.getGroup())
                && a.getOrder() == b.getOrder()
                && a.isActive() == b.isActive()
                && eq(a.getOptions(), b.getOptions());
    }

    private static boolean eq(Object a, Object b) { return a == null ? b == null : a.equals(b); }

    private List<SeedQuestion> load() throws Exception {
        ClassPathResource resource = new ClassPathResource(SEED_FILE);
        if (!resource.exists()) return List.of();
        try (InputStream in = resource.getInputStream()) {
            SeedQuestion[] arr = objectMapper.readValue(in, SeedQuestion[].class);
            return new ArrayList<>(List.of(arr));
        }
    }
}
