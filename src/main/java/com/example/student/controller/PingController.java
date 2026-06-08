package com.example.student.controller;

import com.example.student.repository.ConceptRepository;
import com.example.student.repository.RoadmapRepository;
import com.example.student.repository.SubjectRepository;
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

    public PingController(SubjectRepository subjectRepository,
                          ConceptRepository conceptRepository,
                          RoadmapRepository roadmapRepository) {
        this.subjectRepository = subjectRepository;
        this.conceptRepository = conceptRepository;
        this.roadmapRepository = roadmapRepository;
    }

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }

    // Public — landing page stats, no auth required
    @GetMapping("/public-stats")
    public Map<String, Long> publicStats() {
        return Map.of(
            "subjectCount",  subjectRepository.count(),
            "conceptCount",  conceptRepository.count(),
            "roadmapCount",  roadmapRepository.count()
        );
    }
}
