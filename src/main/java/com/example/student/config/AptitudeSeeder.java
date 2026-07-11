package com.example.student.config;

import com.example.student.model.AptitudeGroup;
import com.example.student.model.AptitudeTopic;
import com.example.student.repository.AptitudeGroupRepository;
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
 * Seeds the {@code aptitude_groups} and {@code aptitude_topics} collections from
 * {@code resources/seed/aptitude-taxonomy.json} on startup.
 *
 * The taxonomy nests as category → group → topic. Only the 4 category cards are
 * static in the frontend; groups and topics (metadata + the two learning modes)
 * live in the database.
 *
 * Idempotent + self-healing:
 *  - new groups/topics are inserted
 *  - existing ones have their metadata refreshed, keeping their {@code _id}
 *  - authored lesson content (learnIt/crackIt) is preserved and only backfilled
 *  - stale groups/topics no longer in the JSON are pruned
 *
 * No quiz questions are seeded here — those come later.
 */
@Component
@Order(0)
public class AptitudeSeeder {

    private static final Logger log = LoggerFactory.getLogger(AptitudeSeeder.class);
    private static final String SEED_FILE = "seed/aptitude-taxonomy.json";

    private final AptitudeTopicRepository topicRepo;
    private final AptitudeGroupRepository groupRepo;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AptitudeSeeder(AptitudeTopicRepository topicRepo,
                          AptitudeGroupRepository groupRepo,
                          CacheService cacheService) {
        this.topicRepo = topicRepo;
        this.groupRepo = groupRepo;
        this.cacheService = cacheService;
    }

    // ── JSON shape (nested) ──────────────────────────────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SeedCategory {
        public String category;
        public List<SeedGroup> groups = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SeedGroup {
        public String slug;
        public String displayName;
        public String description;
        public String icon;
        public int order;
        public List<AptitudeTopic> topics = new ArrayList<>();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        try {
            List<SeedCategory> taxonomy = load();
            if (taxonomy.isEmpty()) {
                log.warn("Aptitude seeder: no data found in {} — skipping.", SEED_FILE);
                return;
            }

            int gInserted = 0, gUpdated = 0, tInserted = 0, tUpdated = 0;
            Set<String> keepGroups = new HashSet<>();
            Set<String> keepTopics = new HashSet<>();
            List<AptitudeTopic> topicsToInsert = new ArrayList<>();

            for (SeedCategory cat : taxonomy) {
                int groupOrder = 0;
                for (SeedGroup g : cat.groups) {
                    groupOrder++;
                    keepGroups.add(g.slug);

                    // ── Group upsert ──
                    Optional<AptitudeGroup> gExistingOpt = groupRepo.findBySlug(g.slug);
                    if (gExistingOpt.isEmpty()) {
                        groupRepo.save(AptitudeGroup.builder()
                                .category(cat.category)
                                .slug(g.slug)
                                .displayName(g.displayName)
                                .description(g.description)
                                .icon(g.icon)
                                .order(g.order != 0 ? g.order : groupOrder)
                                .isActive(true)
                                .build());
                        gInserted++;
                    } else if (refreshGroup(gExistingOpt.get(), cat.category, g, groupOrder)) {
                        groupRepo.save(gExistingOpt.get());
                        gUpdated++;
                    }

                    // ── Topics upsert ──
                    int topicOrder = 0;
                    for (AptitudeTopic def : g.topics) {
                        topicOrder++;
                        keepTopics.add(def.getTopic());
                        def.setCategory(cat.category);
                        def.setGroup(g.slug);
                        if (def.getOrder() == 0) def.setOrder(topicOrder);
                        def.setActive(true);

                        Optional<AptitudeTopic> tExistingOpt = topicRepo.findByTopic(def.getTopic());
                        if (tExistingOpt.isEmpty()) {
                            topicsToInsert.add(def);
                            tInserted++;
                        } else if (refreshTopic(tExistingOpt.get(), def)) {
                            topicRepo.save(tExistingOpt.get());
                            tUpdated++;
                        }
                    }
                }
            }

            if (!topicsToInsert.isEmpty()) topicRepo.saveAll(topicsToInsert);

            // ── Prune stale entries (JSON is the single source of truth) ──
            List<AptitudeGroup> orphanGroups = new ArrayList<>();
            for (AptitudeGroup g : groupRepo.findAll()) {
                if (!keepGroups.contains(g.getSlug())) orphanGroups.add(g);
            }
            if (!orphanGroups.isEmpty()) groupRepo.deleteAll(orphanGroups);

            List<AptitudeTopic> orphanTopics = new ArrayList<>();
            for (AptitudeTopic t : topicRepo.findAll()) {
                if (!keepTopics.contains(t.getTopic())) orphanTopics.add(t);
            }
            if (!orphanTopics.isEmpty()) topicRepo.deleteAll(orphanTopics);

            boolean changed = gInserted + gUpdated + tInserted + tUpdated
                    + orphanGroups.size() + orphanTopics.size() > 0;
            if (changed) cacheService.evictAll("aptitude");

            log.info("Aptitude seeder: groups[{} new, {} upd, {} pruned] topics[{} new, {} upd, {} pruned].",
                    gInserted, gUpdated, orphanGroups.size(),
                    tInserted, tUpdated, orphanTopics.size());
        } catch (Exception e) {
            log.error("Aptitude seeder failed — app continues without seeding: {}", e.getMessage(), e);
        }
    }

    private boolean refreshGroup(AptitudeGroup existing, String category, SeedGroup def, int fallbackOrder) {
        boolean changed = false;
        int order = def.order != 0 ? def.order : fallbackOrder;
        if (!equalsSafe(existing.getCategory(), category))       { existing.setCategory(category); changed = true; }
        if (!equalsSafe(existing.getDisplayName(), def.displayName)) { existing.setDisplayName(def.displayName); changed = true; }
        if (!equalsSafe(existing.getDescription(), def.description)) { existing.setDescription(def.description); changed = true; }
        if (!equalsSafe(existing.getIcon(), def.icon))           { existing.setIcon(def.icon); changed = true; }
        if (existing.getOrder() != order)                        { existing.setOrder(order); changed = true; }
        if (!existing.isActive())                                { existing.setActive(true); changed = true; }
        return changed;
    }

    /** Keep DB topic metadata in sync with the JSON and backfill lessons. Returns true if changed. */
    private boolean refreshTopic(AptitudeTopic existing, AptitudeTopic def) {
        boolean changed = false;

        if (!equalsSafe(existing.getCategory(), def.getCategory()))       { existing.setCategory(def.getCategory()); changed = true; }
        if (!equalsSafe(existing.getGroup(), def.getGroup()))             { existing.setGroup(def.getGroup()); changed = true; }
        if (!equalsSafe(existing.getDisplayName(), def.getDisplayName())) { existing.setDisplayName(def.getDisplayName()); changed = true; }
        if (!equalsSafe(existing.getDescription(), def.getDescription())) { existing.setDescription(def.getDescription()); changed = true; }
        if (!equalsSafe(existing.getIcon(), def.getIcon()))              { existing.setIcon(def.getIcon()); changed = true; }
        if (!equalsSafe(existing.getDifficulty(), def.getDifficulty()))   { existing.setDifficulty(def.getDifficulty()); changed = true; }
        if (!equalsSafe(existing.getPriority(), def.getPriority()))       { existing.setPriority(def.getPriority()); changed = true; }
        if (existing.getOrder() != def.getOrder())                       { existing.setOrder(def.getOrder()); changed = true; }
        if (!existing.isActive())                                        { existing.setActive(true); changed = true; }

        // Backfill lessons only when the DB has none (protects any live edits).
        if (existing.getLearnIt() == null && def.getLearnIt() != null) { existing.setLearnIt(def.getLearnIt()); changed = true; }
        if (existing.getCrackIt() == null && def.getCrackIt() != null) { existing.setCrackIt(def.getCrackIt()); changed = true; }

        // Videos are curated in the JSON (source of truth) — sync whenever the JSON
        // provides a list that differs. An empty/absent JSON list leaves the DB
        // untouched (so we never wipe existing videos by omission).
        if (def.getVideos() != null && !def.getVideos().isEmpty()
                && !sameVideos(existing.getVideos(), def.getVideos())) {
            existing.setVideos(def.getVideos());
            changed = true;
        }

        return changed;
    }

    /** Compare two video lists by their URLs (order-sensitive) — enough to detect edits. */
    private static boolean sameVideos(List<AptitudeTopic.Video> a, List<AptitudeTopic.Video> b) {
        if (a == null) return b == null;
        if (b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!equalsSafe(a.get(i).getUrl(), b.get(i).getUrl())) return false;
        }
        return true;
    }

    private static boolean equalsSafe(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private List<SeedCategory> load() throws Exception {
        ClassPathResource resource = new ClassPathResource(SEED_FILE);
        if (!resource.exists()) return List.of();
        try (InputStream in = resource.getInputStream()) {
            SeedCategory[] arr = objectMapper.readValue(in, SeedCategory[].class);
            return new ArrayList<>(List.of(arr));
        }
    }
}
