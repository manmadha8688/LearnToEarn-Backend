package com.example.student.controller;

import com.example.student.repository.ConceptRepository;
import com.example.student.repository.RoadmapRepository;
import com.example.student.repository.SubjectRepository;
import com.example.student.service.CacheService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PingController {

    private final SubjectRepository subjectRepository;
    private final ConceptRepository conceptRepository;
    private final RoadmapRepository roadmapRepository;
    private final CacheService cacheService;

    public PingController(SubjectRepository subjectRepository,
                          ConceptRepository conceptRepository,
                          RoadmapRepository roadmapRepository,
                          CacheService cacheService) {
        this.subjectRepository = subjectRepository;
        this.conceptRepository = conceptRepository;
        this.roadmapRepository = roadmapRepository;
        this.cacheService = cacheService;
    }

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }

    // Public — landing page stats, no auth required. Cached (5 min) so this unauthenticated
    // endpoint can't be spammed into repeated count() queries against the DB.
    @GetMapping("/public-stats")
    public Map<String, Long> publicStats() {
        return Map.of(
            "subjectCount",  cacheService.getLong("publicStats", "subjects", subjectRepository::count),
            "conceptCount",  cacheService.getLong("publicStats", "concepts", conceptRepository::count),
            "roadmapCount",  cacheService.getLong("publicStats", "roadmaps", roadmapRepository::count)
        );
    }
}
