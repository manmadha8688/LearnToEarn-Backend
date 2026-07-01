package com.example.student.config;

import com.example.student.model.Concept;
import com.example.student.model.Subject;
import com.example.student.repository.ConceptRepository;
import com.example.student.repository.MissionRepository;
import com.example.student.repository.ProblemRepository;
import com.example.student.repository.RoadmapRepository;
import com.example.student.repository.RoadmapSubjectRepository;
import com.example.student.repository.SubjectRepository;
import com.example.student.service.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * On startup: fill Caffeine from Redis (prod) or MongoDB (local) so the
 * very first request after a restart is sub-millisecond.
 * Wrapped in try-catch — any failure never prevents the app from starting.
 */
@Component
public class CacheWarmup {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmup.class);

    private final CacheService cacheService;
    private final SubjectRepository subjectRepository;
    private final ConceptRepository conceptRepository;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapSubjectRepository roadmapSubjectRepository;
    private final MissionRepository missionRepository;
    private final ProblemRepository problemRepository;

    public CacheWarmup(CacheService cacheService,
                       SubjectRepository subjectRepository,
                       ConceptRepository conceptRepository,
                       RoadmapRepository roadmapRepository,
                       RoadmapSubjectRepository roadmapSubjectRepository,
                       MissionRepository missionRepository,
                       ProblemRepository problemRepository) {
        this.cacheService = cacheService;
        this.subjectRepository = subjectRepository;
        this.conceptRepository = conceptRepository;
        this.roadmapRepository = roadmapRepository;
        this.roadmapSubjectRepository = roadmapSubjectRepository;
        this.missionRepository = missionRepository;
        this.problemRepository = problemRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        try {
            log.info("Cache warmup starting...");
            warmSubjectsAndConcepts();
            warmRoadmaps();
            warmMissions();
            warmProblems();
            log.info("Cache warmup complete.");
        } catch (Exception e) {
            log.error("Cache warmup failed — app will continue without warm cache: {}", e.getMessage());
        }
    }

    // ─── Subjects + Concepts ─────────────────────────────────────────────────

    private void warmSubjectsAndConcepts() {
        List<Subject> subjects = cacheService.get("subjects", "all", subjectRepository::findAll);
        if (subjects == null || subjects.isEmpty()) {
            log.info("Cache warmup: no subjects found.");
            return;
        }
        log.info("Cache warmup: {} subjects", subjects.size());

        for (Subject s : subjects) {
            final Subject ref = s;
            cacheService.get("subjects", "id:" + s.getId(), () -> ref);

            List<Concept> concepts = cacheService.get(
                "concepts", "subject:" + s.getId(),
                () -> conceptRepository.findBySubjectIdOrderByOrderIndex(s.getId())
            );

            if (concepts != null) {
                for (Concept c : concepts) {
                    final Concept cRef = c;
                    cacheService.get("concepts", "id:" + c.getId(), () -> cRef);
                }
                long count = concepts.size();
                cacheService.get("concepts", "count:" + s.getId(), () -> count);
            }
        }
        cacheService.get("concepts", "total", conceptRepository::count);
    }

    // ─── Roadmaps ────────────────────────────────────────────────────────────

    private void warmRoadmaps() {
        var all      = cacheService.get("roadmaps", "all",       roadmapRepository::findAll);
        var published = cacheService.get("roadmaps", "published", roadmapRepository::findByIsPublishedTrue);
        int count = published == null ? 0 : published.size();
        log.info("Cache warmup: {} roadmaps", count);
        // Warm per-roadmap subject lists
        if (published != null) {
            for (var r : published) {
                final String rid = r.getId();
                cacheService.get("roadmaps", "subjects:" + rid,
                        () -> roadmapSubjectRepository.findByRoadmapIdOrderByOrderIndex(rid));
            }
        }
    }

    // ─── Missions ────────────────────────────────────────────────────────────

    private void warmMissions() {
        var missions = cacheService.get("missions", "all",
                missionRepository::findByPublishedTrueOrderByOrderIndexAsc);
        int count = missions == null ? 0 : missions.size();
        log.info("Cache warmup: {} missions", count);
    }

    // ─── Problems ────────────────────────────────────────────────────────────

    private void warmProblems() {
        cacheService.get("problems", "all",
                problemRepository::findAllByOrderByOrderIndexAsc);

        for (String track : List.of("START_CODING", "LOGIC_BUILDING", "SKILL_UP", "INTERVIEW_PREP", "SCENARIO_CODING")) {
            final String t = track;
            cacheService.get("problems", "track:" + t,
                    () -> problemRepository.findByTracksContainingOrderByOrderIndexAsc(t));
        }
        log.info("Cache warmup: problems warmed for all 5 tracks");
    }
}
