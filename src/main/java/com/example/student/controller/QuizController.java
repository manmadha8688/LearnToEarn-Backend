package com.example.student.controller;

import com.example.student.dto.QuizSubmitRequest;
import com.example.student.model.User;
import com.example.student.service.QuizService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/concept/{conceptId}/start")
    public ResponseEntity<?> startConcept(@PathVariable String conceptId,
                                           @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(quizService.startConceptQuiz(conceptId, user.getId()));
    }

    @PostMapping("/subject/{subjectId}/start")
    public ResponseEntity<?> startSubject(@PathVariable String subjectId,
                                           @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(quizService.startSubjectQuiz(subjectId, user.getId()));
    }

    @PostMapping("/roadmap/{roadmapId}/start")
    public ResponseEntity<?> startRoadmap(@PathVariable String roadmapId,
                                           @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(quizService.startRoadmapQuiz(roadmapId, user.getId()));
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submit(@Valid @RequestBody QuizSubmitRequest req,
                                     @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(quizService.submitQuiz(req, user.getId()));
    }

    @GetMapping("/attempt/{attemptId}")
    public ResponseEntity<?> getAttemptResult(@PathVariable String attemptId,
                                               @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(quizService.getAttemptResult(attemptId, user.getId()));
    }

    @GetMapping("/{type}/{refId}/status")
    public ResponseEntity<?> getStatus(@PathVariable String type,
                                        @PathVariable String refId,
                                        @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(quizService.getAttemptStatus(type, refId, user.getId()));
    }

    @GetMapping("/subject/{subjectId}/status")
    public ResponseEntity<?> getSubjectStatus(@PathVariable String subjectId,
                                               @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(quizService.getSubjectStatus(subjectId, user.getId()));
    }

    @GetMapping("/subjects/bulk-status")
    public ResponseEntity<?> getBulkSubjectStatus(@RequestParam List<String> ids,
                                                   @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(quizService.getBulkSubjectStatus(ids, user.getId()));
    }

    @GetMapping("/roadmap/{roadmapId}/status")
    public ResponseEntity<?> getRoadmapStatus(@PathVariable String roadmapId,
                                               @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(quizService.getRoadmapStatus(roadmapId, user.getId()));
    }
}
