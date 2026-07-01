package com.example.student.config;

import com.example.student.model.Feedback;
import com.example.student.model.Report;
import com.example.student.model.RoadmapSubject;
import com.example.student.model.WalkIn;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One-time, idempotent data-integrity migration. Runs first (highest precedence)
 * so the collection is clean before any index is created.
 *
 * <p>Why this exists: {@link RoadmapSubject} declares a UNIQUE compound index on
 * {roadmapId, subjectId}, but historical data contained the same subject linked to
 * a roadmap more than once. That made a subject appear twice in a roadmap and made
 * {@code spring.data.mongodb.auto-index-creation=true} crash the app on startup.
 *
 * <p>Rather than trust automatic index creation (which runs at an unpredictable time
 * relative to startup runners), we keep it disabled and manage indexes explicitly and
 * deterministically here: de-duplicate first, then ensure the indexes.
 *
 * <p>Safe to run on every startup — it only removes exact duplicate links (keeping one)
 * and {@code ensureIndex} is a no-op when the index already exists.
 */
@Component
@Order(0)
public class DataIntegrityMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataIntegrityMigration.class);

    private final MongoTemplate mongoTemplate;

    public DataIntegrityMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(String... args) {
        dedupeRoadmapSubjects();
        ensureIndexes();
    }

    /** Remove duplicate (roadmapId, subjectId) links, keeping the lowest orderIndex. */
    private void dedupeRoadmapSubjects() {
        List<RoadmapSubject> all = mongoTemplate.findAll(RoadmapSubject.class);
        Map<String, List<RoadmapSubject>> groups = all.stream()
                .collect(Collectors.groupingBy(rs -> rs.getRoadmapId() + "|" + rs.getSubjectId()));

        List<String> idsToDelete = new ArrayList<>();
        for (List<RoadmapSubject> group : groups.values()) {
            if (group.size() <= 1) continue;
            group.sort(Comparator.comparingInt(RoadmapSubject::getOrderIndex));
            for (int i = 1; i < group.size(); i++) {
                idsToDelete.add(group.get(i).getId());
            }
        }

        if (idsToDelete.isEmpty()) {
            log.info("DataIntegrityMigration: roadmap_subjects clean — no duplicates found");
            return;
        }
        long removed = mongoTemplate.remove(
                Query.query(Criteria.where("_id").in(idsToDelete)), RoadmapSubject.class)
                .getDeletedCount();
        log.info("DataIntegrityMigration: removed {} duplicate roadmap_subject link(s)", removed);
    }

    /**
     * Create the indexes the models declare, now that data is clean. Each is only created
     * if an index on the same key(s) doesn't already exist, so this stays quiet and
     * idempotent across restarts regardless of how existing indexes were named.
     */
    private void ensureIndexes() {
        ensureUnique(RoadmapSubject.class,
                new Document("roadmapId", 1).append("subjectId", 1),
                List.of("roadmapId", "subjectId"), "roadmap_subjects{roadmapId,subjectId}");

        ensureSimple(Report.class, "status", Sort.Direction.ASC);
        ensureSimple(Feedback.class, "createdAt", Sort.Direction.DESC);
        ensureSimple(WalkIn.class, "city", Sort.Direction.ASC);
        ensureSimple(WalkIn.class, "status", Sort.Direction.ASC);
    }

    private boolean indexExistsForKeys(Class<?> type, List<String> keys) {
        return mongoTemplate.indexOps(type).getIndexInfo().stream().anyMatch(info ->
                info.getIndexFields().stream().map(IndexField::getKey).collect(Collectors.toList()).equals(keys));
    }

    private void ensureSimple(Class<?> type, String field, Sort.Direction dir) {
        try {
            if (indexExistsForKeys(type, List.of(field))) return;
            mongoTemplate.indexOps(type).ensureIndex(new Index(field, dir));
            log.info("DataIntegrityMigration: created index {}.{}", type.getSimpleName(), field);
        } catch (Exception e) {
            log.warn("DataIntegrityMigration: index {}.{} — {}", type.getSimpleName(), field, e.getMessage());
        }
    }

    private void ensureUnique(Class<?> type, Document keyDoc, List<String> keys, String label) {
        try {
            if (indexExistsForKeys(type, keys)) return;
            mongoTemplate.indexOps(type).ensureIndex(new CompoundIndexDefinition(keyDoc).unique());
            log.info("DataIntegrityMigration: created unique index {}", label);
        } catch (Exception e) {
            log.warn("DataIntegrityMigration: unique index {} — {}", label, e.getMessage());
        }
    }
}
