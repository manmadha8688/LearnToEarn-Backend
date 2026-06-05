package com.example.student.config;

import com.example.student.model.Concept;
import com.example.student.model.Subject;
import com.example.student.repository.ConceptRepository;
import com.example.student.repository.SubjectRepository;
import com.example.student.service.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * On startup: pull subjects + concepts from Redis (prod) or MongoDB (local)
 * and fill Caffeine so the very first request after a restart is sub-millisecond.
 * Wrapped in try-catch — a Redis failure never prevents the app from starting.
 */
@Component
public class CacheWarmup {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmup.class);

    private final CacheService cacheService;
    private final SubjectRepository subjectRepository;
    private final ConceptRepository conceptRepository;

    public CacheWarmup(CacheService cacheService,
                       SubjectRepository subjectRepository,
                       ConceptRepository conceptRepository) {
        this.cacheService = cacheService;
        this.subjectRepository = subjectRepository;
        this.conceptRepository = conceptRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        try {
            log.info("Cache warmup starting...");

            List<Subject> subjects = cacheService.get("subjects", "all", subjectRepository::findAll);
            if (subjects == null || subjects.isEmpty()) {
                log.info("Cache warmup: no subjects found, skipping.");
                return;
            }
            log.info("Cache warmup: {} subjects loaded", subjects.size());

            for (Subject s : subjects) {
                final Subject subjectRef = s;
                cacheService.get("subjects", "id:" + s.getId(), () -> subjectRef);

                List<Concept> concepts = cacheService.get(
                    "concepts", "subject:" + s.getId(),
                    () -> conceptRepository.findBySubjectIdOrderByOrderIndex(s.getId())
                );

                if (concepts != null) {
                    for (Concept c : concepts) {
                        final Concept conceptRef = c;
                        cacheService.get("concepts", "id:" + c.getId(), () -> conceptRef);
                    }
                    long count = concepts.size();
                    cacheService.get("concepts", "count:" + s.getId(), () -> count);
                }
            }

            log.info("Cache warmup complete.");
        } catch (Exception e) {
            log.error("Cache warmup failed — app will continue without warm cache: {}", e.getMessage());
        }
    }
}
