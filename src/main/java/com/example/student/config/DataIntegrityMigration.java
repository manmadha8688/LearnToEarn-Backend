package com.example.student.config;

import com.example.student.model.Bookmark;
import com.example.student.model.Concept;
import com.example.student.model.Feedback;
import com.example.student.model.MissionSubmission;
import com.example.student.model.QuizAttempt;
import com.example.student.model.Report;
import com.example.student.model.Resume;
import com.example.student.model.RoadmapSubject;
import com.example.student.model.User;
import com.example.student.model.UserConceptProgress;
import com.example.student.model.UserRoadmapEnrollment;
import com.example.student.model.UserSubjectBadge;
import com.example.student.model.WalkIn;
import com.example.student.repository.UserRepository;
import com.example.student.service.UsernameService;
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
import org.springframework.data.mongodb.core.query.Update;
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
    private final UserRepository userRepository;
    private final UsernameService usernameService;

    public DataIntegrityMigration(MongoTemplate mongoTemplate,
                                  UserRepository userRepository,
                                  UsernameService usernameService) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.usernameService = usernameService;
    }

    @Override
    public void run(String... args) {
        dedupeRoadmapSubjects();
        backfillUsernames();
        backfillProviders();
        dropLegacyConceptFields();
        migrateResumeSharedFlag();
        ensureIndexes();
    }

    /**
     * Legacy Resume used a boolean field named {@code isPublic}. Lombok + Spring Data
     * MongoDB often persisted that incorrectly (as {@code public} or never at all).
     * Copy any truthy legacy flag onto the new {@code shared} field, then drop the old keys.
     */
    private void migrateResumeSharedFlag() {
        try {
            Query q = new Query(new Criteria().orOperator(
                    Criteria.where("isPublic").is(true),
                    Criteria.where("public").is(true),
                    Criteria.where("isPublic").exists(true),
                    Criteria.where("public").exists(true)));
            long pending = mongoTemplate.count(q, Resume.class);
            if (pending == 0) {
                log.info("DataIntegrityMigration: resumes.shared clean — no legacy flags");
                return;
            }
            // Promote truthy legacy flags to shared=true.
            Query promote = new Query(new Criteria().orOperator(
                    Criteria.where("isPublic").is(true),
                    Criteria.where("public").is(true)));
            long promoted = mongoTemplate.updateMulti(promote,
                    new Update().set("shared", true), Resume.class).getModifiedCount();
            // Drop the ambiguous legacy keys from every resume that still has them.
            long cleaned = mongoTemplate.updateMulti(q,
                    new Update().unset("isPublic").unset("public"), Resume.class).getModifiedCount();
            log.info("DataIntegrityMigration: resumes.shared — promoted {}, cleaned {}", promoted, cleaned);
        } catch (Exception e) {
            log.warn("DataIntegrityMigration: resumes.shared migration — {}", e.getMessage());
        }
    }

    /**
     * Remove the retired legacy concept fields (whatItIs, whyItMatters, codeExample) from
     * every concept document. All concept content now lives in introduction/explanationSimple/
     * examples. Idempotent: only touches documents that still carry any of the fields; a no-op
     * once the collection is clean.
     */
    private void dropLegacyConceptFields() {
        Query q = new Query(new Criteria().orOperator(
                Criteria.where("whatItIs").exists(true),
                Criteria.where("whyItMatters").exists(true),
                Criteria.where("codeExample").exists(true)));
        long pending = mongoTemplate.count(q, Concept.class);
        if (pending == 0) {
            log.info("DataIntegrityMigration: concepts clean — no legacy fields to drop");
            return;
        }
        Update u = new Update().unset("whatItIs").unset("whyItMatters").unset("codeExample");
        long modified = mongoTemplate.updateMulti(q, u, Concept.class).getModifiedCount();
        log.info("DataIntegrityMigration: dropped legacy fields from {} concept(s)", modified);
    }

    /**
     * Existing non-guest accounts predate multi-provider auth — they were all created with
     * email + password, so mark them as local-provider accounts. Idempotent: only touches
     * users whose providers list is missing/empty.
     */
    private void backfillProviders() {
        Query q = new Query(new Criteria().andOperator(
                Criteria.where("role").ne("GUEST"),
                new Criteria().orOperator(
                        Criteria.where("providers").exists(false),
                        Criteria.where("providers").is(null),
                        Criteria.where("providers").size(0))
        ));
        List<User> missing = mongoTemplate.find(q, User.class);
        if (missing.isEmpty()) {
            log.info("DataIntegrityMigration: providers clean — no backfill needed");
            return;
        }
        int assigned = 0;
        for (User u : missing) {
            u.setProviders(new ArrayList<>(List.of("local")));
            userRepository.save(u);
            assigned++;
        }
        log.info("DataIntegrityMigration: backfilled providers=[local] for {} user(s)", assigned);
    }

    /** Assign a unique username to every non-guest user that predates the profile feature. */
    private void backfillUsernames() {
        Query q = new Query(new Criteria().andOperator(
                Criteria.where("role").ne("GUEST"),
                new Criteria().orOperator(
                        Criteria.where("username").exists(false),
                        Criteria.where("username").is(null),
                        Criteria.where("username").is(""))
        ));
        List<User> missing = mongoTemplate.find(q, User.class);
        if (missing.isEmpty()) {
            log.info("DataIntegrityMigration: usernames clean — no backfill needed");
            return;
        }
        int assigned = 0;
        for (User u : missing) {
            u.setUsername(usernameService.generateUnique(u.getFullName(), u.getEmail()));
            userRepository.save(u);
            assigned++;
        }
        log.info("DataIntegrityMigration: backfilled {} username(s)", assigned);
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

        // Unique handle for public profiles; sparse so guests (no username) don't collide on null.
        ensureUniqueSparse(User.class, new Document("username", 1),
                List.of("username"), "users{username}");

        // One Google account maps to one user; sparse so non-Google users don't collide on null.
        ensureUniqueSparse(User.class, new Document("googleId", 1),
                List.of("googleId"), "users{googleId}");

        // One GitHub account maps to one user; sparse so non-GitHub users don't collide on null.
        ensureUniqueSparse(User.class, new Document("githubId", 1),
                List.of("githubId"), "users{githubId}");

        // One bookmark per (user, type, ref) — prevents duplicates.
        ensureUnique(Bookmark.class,
                new Document("userId", 1).append("type", 1).append("refId", 1),
                List.of("userId", "type", "refId"), "bookmarks{userId,type,refId}");

        // Unique login email (also the DB-level guard against duplicate registration).
        ensureUnique(User.class, new Document("email", 1),
                List.of("email"), "users{email}");

        // Secret guest device token; sparse so the vast majority of users (no token) don't
        // collide on null.
        ensureUniqueSparse(User.class, new Document("guestDeviceToken", 1),
                List.of("guestDeviceToken"), "users{guestDeviceToken}");

        // One progress row per (user, concept).
        ensureUnique(UserConceptProgress.class,
                new Document("userId", 1).append("conceptId", 1),
                List.of("userId", "conceptId"), "user_concept_progress{userId,conceptId}");

        // One badge per (user, subject).
        ensureUnique(UserSubjectBadge.class,
                new Document("userId", 1).append("subjectId", 1),
                List.of("userId", "subjectId"), "user_subject_badges{userId,subjectId}");

        // One enrollment per (user, roadmap).
        ensureUnique(UserRoadmapEnrollment.class,
                new Document("userId", 1).append("roadmapId", 1),
                List.of("userId", "roadmapId"), "user_roadmap_enrollments{userId,roadmapId}");

        // Quiz attempt lookups always filter by (user, type, ref) — non-unique (many attempts).
        ensureCompound(QuizAttempt.class,
                new Document("userId", 1).append("type", 1).append("refId", 1),
                List.of("userId", "type", "refId"), "quiz_attempts{userId,type,refId}");

        // Concept lists are fetched per subject.
        ensureSimple(Concept.class, "subjectId", Sort.Direction.ASC);

        // Public shareable resume slug; sparse so unshared resumes (no slug) don't collide on null.
        ensureUniqueSparse(Resume.class, new Document("shareSlug", 1),
                List.of("shareSlug"), "resumes{shareSlug}");

        // One GitHub repo can only be linked to one mission submission globally.
        ensureUniqueSparse(MissionSubmission.class, new Document("repoKey", 1),
                List.of("repoKey"), "mission_submissions{repoKey}");
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

    private void ensureCompound(Class<?> type, Document keyDoc, List<String> keys, String label) {
        try {
            if (indexExistsForKeys(type, keys)) return;
            mongoTemplate.indexOps(type).ensureIndex(new CompoundIndexDefinition(keyDoc));
            log.info("DataIntegrityMigration: created index {}", label);
        } catch (Exception e) {
            log.warn("DataIntegrityMigration: index {} — {}", label, e.getMessage());
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

    private void ensureUniqueSparse(Class<?> type, Document keyDoc, List<String> keys, String label) {
        try {
            if (indexExistsForKeys(type, keys)) return;
            mongoTemplate.indexOps(type).ensureIndex(new CompoundIndexDefinition(keyDoc).unique().sparse());
            log.info("DataIntegrityMigration: created unique sparse index {}", label);
        } catch (Exception e) {
            log.warn("DataIntegrityMigration: unique sparse index {} — {}", label, e.getMessage());
        }
    }
}
