package com.example.student.controller;

import com.example.student.model.Mission;
import com.example.student.repository.MissionRepository;
import com.example.student.service.CacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/missions")
public class MissionController {

    private final MissionRepository missionRepository;
    private final CacheService cacheService;

    public MissionController(MissionRepository missionRepository, CacheService cacheService) {
        this.missionRepository = missionRepository;
        this.cacheService = cacheService;
    }

    // Public — list page
    @GetMapping
    public ResponseEntity<List<Mission>> getAll() {
        List<Mission> missions = cacheService.get("missions", "all",
                () -> missionRepository.findByPublishedTrueOrderByOrderIndexAsc());
        return ResponseEntity.ok(missions);
    }

    // Auth required — detail page
    @GetMapping("/{id}")
    public ResponseEntity<Mission> getOne(@PathVariable String id) {
        Mission m = cacheService.get("missions", "id:" + id,
                () -> missionRepository.findById(id).orElse(null));
        return m != null ? ResponseEntity.ok(m) : ResponseEntity.notFound().build();
    }
}
