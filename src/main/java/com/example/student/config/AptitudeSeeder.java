package com.example.student.config;

import com.example.student.model.AptitudeTopic;
import com.example.student.repository.AptitudeTopicRepository;
import com.example.student.service.CacheService;
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
 * Seeds the {@code aptitude_topics} collection from
 * {@code resources/seed/aptitude-topics.json} on startup.
 *
 * The JSON is the single authoring source — ALL topic data (metadata + the two
 * learning modes) lives in the database after seeding. Only the 4 category cards
 * are static in the frontend.
 *
 * Idempotent + self-healing:
 *  - new slugs are inserted
 *  - existing topics have their metadata refreshed and any newly-authored
 *    learnIt/crackIt lesson content backfilled, without changing their _id
 *
 * No quiz questions are seeded here — those come later.
 */
@Component
@Order(0)
public class AptitudeSeeder {

    private static final Logger log = LoggerFactory.getLogger(AptitudeSeeder.class);
    private static final String SEED_FILE = "seed/aptitude-topics.json";

    private final AptitudeTopicRepository repo;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AptitudeSeeder(AptitudeTopicRepository repo, CacheService cacheService) {
        this.repo = repo;
        this.cacheService = cacheService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        try {
            List<AptitudeTopic> defaults = load();
            if (defaults.isEmpty()) {
                log.warn("Aptitude seeder: no topics found in {} — skipping.", SEED_FILE);
                return;
            }

            List<AptitudeTopic> toInsert = new ArrayList<>();
            int updated = 0;

            for (AptitudeTopic def : defaults) {
                def.setActive(true);
                Optional<AptitudeTopic> existingOpt = repo.findByTopic(def.getTopic());
                if (existingOpt.isEmpty()) {
                    toInsert.add(def);
                    continue;
                }
                AptitudeTopic existing = existingOpt.get();
                if (refresh(existing, def)) {
                    repo.save(existing);
                    updated++;
                }
            }

            if (!toInsert.isEmpty()) repo.saveAll(toInsert);

            // Prune: the JSON is the single source of truth. Delete any topic whose
            // slug is no longer defined so the collection has zero stale/duplicate
            // entries (e.g. after a topic is split or renamed). Authored lessons on
            // still-defined slugs (percentages, time-speed-distance) are untouched.
            Set<String> keep = new HashSet<>();
            for (AptitudeTopic def : defaults) keep.add(def.getTopic());
            List<AptitudeTopic> orphans = new ArrayList<>();
            for (AptitudeTopic t : repo.findAll()) {
                if (!keep.contains(t.getTopic())) orphans.add(t);
            }
            if (!orphans.isEmpty()) repo.deleteAll(orphans);

            if (!toInsert.isEmpty() || updated > 0 || !orphans.isEmpty()) cacheService.evictAll("aptitude");

            log.info("Aptitude seeder: {} inserted, {} updated, {} pruned, {} total defined.",
                    toInsert.size(), updated, orphans.size(), defaults.size());
        } catch (Exception e) {
            log.error("Aptitude seeder failed — app continues without seeding: {}", e.getMessage(), e);
        }
    }

    /** Keep DB metadata in sync with the JSON and backfill lessons. Returns true if changed. */
    private boolean refresh(AptitudeTopic existing, AptitudeTopic def) {
        boolean changed = false;

        if (!equalsSafe(existing.getCategory(), def.getCategory()))       { existing.setCategory(def.getCategory()); changed = true; }
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

        // Videos are curated in the JSON (source of truth) — keep DB in sync whenever
        // the JSON provides a list that differs. An empty/absent JSON list leaves the
        // DB untouched (so we never wipe existing videos by omission).
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

    private List<AptitudeTopic> load() throws Exception {
        ClassPathResource resource = new ClassPathResource(SEED_FILE);
        if (!resource.exists()) return List.of();
        try (InputStream in = resource.getInputStream()) {
            AptitudeTopic[] arr = objectMapper.readValue(in, AptitudeTopic[].class);
            return new ArrayList<>(List.of(arr));
        }
    }
}
