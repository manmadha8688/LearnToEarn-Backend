package com.example.student.controller;

import com.example.student.model.Mission;
import com.example.student.repository.MissionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/missions")
public class MissionController {

    private final MissionRepository missionRepository;

    public MissionController(MissionRepository missionRepository) {
        this.missionRepository = missionRepository;
    }

    // Public — list page visible without login
    @GetMapping
    public ResponseEntity<List<Mission>> getAll() {
        return ResponseEntity.ok(missionRepository.findByPublishedTrueOrderByOrderIndexAsc());
    }

    // Auth required — detail page requires login
    @GetMapping("/{id}")
    public ResponseEntity<Mission> getOne(@PathVariable String id) {
        return missionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
