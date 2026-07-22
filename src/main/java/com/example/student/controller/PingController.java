package com.example.student.controller;

import com.example.student.repository.ConceptRepository;
import com.example.student.repository.RoadmapRepository;
import com.example.student.repository.SubjectRepository;
import com.example.student.service.CacheService;
import com.example.student.service.CodeExecutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PingController {

    private static final long MB = 1024 * 1024;

    private final SubjectRepository subjectRepository;
    private final ConceptRepository conceptRepository;
    private final RoadmapRepository roadmapRepository;
    private final CacheService cacheService;
    private final CodeExecutionService codeExecutionService;

    public PingController(SubjectRepository subjectRepository,
                          ConceptRepository conceptRepository,
                          RoadmapRepository roadmapRepository,
                          CacheService cacheService,
                          CodeExecutionService codeExecutionService) {
        this.subjectRepository = subjectRepository;
        this.conceptRepository = conceptRepository;
        this.roadmapRepository = roadmapRepository;
        this.cacheService = cacheService;
        this.codeExecutionService = codeExecutionService;
    }

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }

    // Public health probe with live runtime metrics — no auth, no sensitive data.
    // Used by uptime monitors and the load-shedding safeguard for observability.
    @GetMapping("/health")
    public Map<String, Object> health() {
        Runtime rt = Runtime.getRuntime();
        long max  = rt.maxMemory();
        long used = rt.totalMemory() - rt.freeMemory();
        long freeVsMax = max - used;

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("usedMB", used / MB);
        memory.put("maxMB", max / MB);
        memory.put("freeMB", freeVsMax / MB);
        memory.put("percentUsed", max > 0 ? Math.round(used * 100.0 / max) : 0);

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        Map<String, Object> threadInfo = new LinkedHashMap<>();
        threadInfo.put("active", threads.getThreadCount());
        threadInfo.put("peak", threads.getPeakThreadCount());

        Map<String, Object> codeExec = new LinkedHashMap<>();
        codeExec.put("activeSlots", codeExecutionService.activeSlots());
        codeExec.put("maxSlots", codeExecutionService.maxSlots());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("memory", memory);
        body.put("threads", threadInfo);
        body.put("codeExecution", codeExec);
        return body;
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
