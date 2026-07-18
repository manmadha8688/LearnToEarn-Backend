package com.example.student.controller;

import com.example.student.dto.AdminConceptRequest;
import com.example.student.dto.AdminQuestionRequest;
import com.example.student.dto.AdminRoadmapRequest;
import com.example.student.dto.AdminSubjectRequest;
import com.example.student.model.Mission;
import com.example.student.model.ProblemQuestion;
import com.example.student.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String filter) {
        return ResponseEntity.ok(adminService.getUsers(page, size, search, filter));
    }

    @GetMapping("/users/{id}/progress")
    public ResponseEntity<?> getUserProgress(@PathVariable String id) {
        return ResponseEntity.ok(adminService.getUserProgress(id));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        adminService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "User deleted"));
    }

    @GetMapping("/subjects")
    public ResponseEntity<?> getAllSubjects() {
        return ResponseEntity.ok(adminService.getAllSubjects());
    }

    @PostMapping("/subjects")
    public ResponseEntity<?> createSubject(@Valid @RequestBody AdminSubjectRequest req) {
        return ResponseEntity.ok(adminService.createSubject(req));
    }

    @PutMapping("/subjects/{id}")
    public ResponseEntity<?> updateSubject(@PathVariable String id, @Valid @RequestBody AdminSubjectRequest req) {
        return ResponseEntity.ok(adminService.updateSubject(id, req));
    }

    @DeleteMapping("/subjects/{id}")
    public ResponseEntity<?> deleteSubject(@PathVariable String id) {
        adminService.deleteSubject(id);
        return ResponseEntity.ok(Map.of("message", "Subject deleted"));
    }

    @GetMapping("/concepts")
    public ResponseEntity<?> getConceptsBySubject(@RequestParam(required = false) String subjectId) {
        if (subjectId != null) return ResponseEntity.ok(adminService.getConceptsBySubject(subjectId));
        return ResponseEntity.ok(adminService.getAllSubjects());
    }

    @PostMapping("/concepts")
    public ResponseEntity<?> createConcept(@Valid @RequestBody AdminConceptRequest req) {
        return ResponseEntity.ok(adminService.createConcept(req));
    }

    @PutMapping("/concepts/{id}")
    public ResponseEntity<?> updateConcept(@PathVariable String id, @Valid @RequestBody AdminConceptRequest req) {
        return ResponseEntity.ok(adminService.updateConcept(id, req));
    }

    @DeleteMapping("/concepts/{id}")
    public ResponseEntity<?> deleteConcept(@PathVariable String id) {
        adminService.deleteConcept(id);
        return ResponseEntity.ok(Map.of("message", "Concept deleted"));
    }

    @GetMapping("/roadmaps")
    public ResponseEntity<?> getAllRoadmaps() {
        return ResponseEntity.ok(adminService.getAllRoadmaps());
    }

    @PostMapping("/roadmaps")
    public ResponseEntity<?> createRoadmap(@Valid @RequestBody AdminRoadmapRequest req) {
        return ResponseEntity.ok(adminService.createRoadmap(req));
    }

    @PutMapping("/roadmaps/{id}")
    public ResponseEntity<?> updateRoadmap(@PathVariable String id, @Valid @RequestBody AdminRoadmapRequest req) {
        return ResponseEntity.ok(adminService.updateRoadmap(id, req));
    }

    @DeleteMapping("/roadmaps/{id}")
    public ResponseEntity<?> deleteRoadmap(@PathVariable String id) {
        adminService.deleteRoadmap(id);
        return ResponseEntity.ok(Map.of("message", "Roadmap deleted"));
    }

    @GetMapping("/roadmaps/{id}/subjects")
    public ResponseEntity<?> getRoadmapSubjects(@PathVariable String id) {
        return ResponseEntity.ok(adminService.getRoadmapSubjects(id));
    }

    @PostMapping("/roadmaps/{id}/subjects")
    public ResponseEntity<?> addSubjectToRoadmap(@PathVariable String id,
                                                  @Valid @RequestBody Map<String, Object> body) {
        Object subjectIdObj = body.get("subjectId");
        if (subjectIdObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "subjectId is required"));
        }
        String subjectId = subjectIdObj.toString();
        int orderIndex = body.containsKey("orderIndex")
                ? Integer.parseInt(body.get("orderIndex").toString()) : 0;
        return ResponseEntity.ok(adminService.addSubjectToRoadmap(id, subjectId, orderIndex));
    }

    @DeleteMapping("/roadmaps/{roadmapId}/subjects/{subjectId}")
    public ResponseEntity<?> removeSubjectFromRoadmap(@PathVariable String roadmapId,
                                                       @PathVariable String subjectId) {
        adminService.removeSubjectFromRoadmap(roadmapId, subjectId);
        return ResponseEntity.ok(Map.of("message", "Subject removed"));
    }

    @PutMapping("/roadmaps/{roadmapId}/subjects/{subjectId}/reorder")
    public ResponseEntity<?> reorderSubject(@PathVariable String roadmapId,
                                             @PathVariable String subjectId,
                                             @Valid @RequestBody Map<String, Integer> body) {
        Integer newOrderIndex = body.get("newOrderIndex");
        if (newOrderIndex == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "newOrderIndex is required"));
        }
        return ResponseEntity.ok(adminService.reorderSubjectInRoadmap(
                roadmapId, subjectId, newOrderIndex));
    }

    // ─── QUESTIONS ───────────────────────────────────────────────────────────

    @GetMapping("/questions/concept/{conceptId}")
    public ResponseEntity<?> getQuestions(@PathVariable String conceptId) {
        return ResponseEntity.ok(adminService.getQuestionsByConceptId(conceptId));
    }

    @PostMapping("/questions")
    public ResponseEntity<?> createQuestion(@Valid @RequestBody AdminQuestionRequest req) {
        return ResponseEntity.ok(adminService.createQuestion(req));
    }

    @PutMapping("/questions/{id}")
    public ResponseEntity<?> updateQuestion(@PathVariable String id,
                                             @Valid @RequestBody AdminQuestionRequest req) {
        return ResponseEntity.ok(adminService.updateQuestion(id, req));
    }

    @DeleteMapping("/questions/{id}")
    public ResponseEntity<?> deleteQuestion(@PathVariable String id) {
        adminService.deleteQuestion(id);
        return ResponseEntity.ok(Map.of("message", "Question deleted"));
    }

    // ─── MISSIONS ────────────────────────────────────────────────────────────

    @GetMapping("/missions")
    public ResponseEntity<?> getAllMissions() {
        return ResponseEntity.ok(adminService.getAllMissions());
    }

    @PostMapping("/missions")
    public ResponseEntity<?> createMission(@RequestBody Mission mission) {
        return ResponseEntity.ok(adminService.createMission(mission));
    }

    @PutMapping("/missions/{id}")
    public ResponseEntity<?> updateMission(@PathVariable String id, @RequestBody Mission mission) {
        return ResponseEntity.ok(adminService.updateMission(id, mission));
    }

    @DeleteMapping("/missions/{id}")
    public ResponseEntity<?> deleteMission(@PathVariable String id) {
        adminService.deleteMission(id);
        return ResponseEntity.ok(Map.of("message", "Mission deleted"));
    }

    // ─── PROBLEMS ────────────────────────────────────────────────────────────

    @GetMapping("/problems")
    public ResponseEntity<?> getAllProblems() {
        return ResponseEntity.ok(adminService.getAllProblems());
    }

    @PostMapping("/problems")
    public ResponseEntity<?> createProblem(@RequestBody ProblemQuestion problem) {
        return ResponseEntity.ok(adminService.createProblem(problem));
    }

    @PutMapping("/problems/{id}")
    public ResponseEntity<?> updateProblem(@PathVariable String id, @RequestBody ProblemQuestion problem) {
        return ResponseEntity.ok(adminService.updateProblem(id, problem));
    }

    @DeleteMapping("/problems/{id}")
    public ResponseEntity<?> deleteProblem(@PathVariable String id) {
        adminService.deleteProblem(id);
        return ResponseEntity.ok(Map.of("message", "Problem deleted"));
    }
}
