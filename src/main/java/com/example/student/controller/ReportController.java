package com.example.student.controller;

import com.example.student.model.Report;
import com.example.student.model.User;
import com.example.student.repository.ReportRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportRepository reportRepository;

    public ReportController(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    // Student submits a report
    @PostMapping
    public ResponseEntity<?> submit(@RequestBody Report report,
                                    @AuthenticationPrincipal User user) {
        report.setUserId(user.getId());
        report.setUserEmail(user.getEmail());
        report.setUserName(user.getFullName());
        report.setStatus("OPEN");
        reportRepository.save(report);
        return ResponseEntity.ok(Map.of("message", "Report submitted. Thank you!"));
    }

    // Admin — list reports, optionally filtered by status
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        PageRequest pr = PageRequest.of(page, size);
        Page<Report> reports = (status != null && !status.isBlank())
                ? reportRepository.findByStatusOrderByCreatedAtDesc(status, pr)
                : reportRepository.findAllByOrderByCreatedAtDesc(pr);
        return ResponseEntity.ok(reports);
    }

    // Admin — update status / add note
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id,
                                    @RequestBody Map<String, String> body) {
        return reportRepository.findById(id).map(r -> {
            if (body.containsKey("status")) {
                r.setStatus(body.get("status"));
                if ("RESOLVED".equals(body.get("status"))) {
                    r.setResolvedAt(LocalDateTime.now());
                }
            }
            if (body.containsKey("adminNote")) r.setAdminNote(body.get("adminNote"));
            reportRepository.save(r);
            return ResponseEntity.ok(r);
        }).orElse(ResponseEntity.notFound().build());
    }

    // Admin — delete
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        reportRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    // Stats for admin dashboard
    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        return ResponseEntity.ok(Map.of(
            "total", reportRepository.count(),
            "open", reportRepository.countByStatus("OPEN"),
            "inProgress", reportRepository.countByStatus("IN_PROGRESS"),
            "resolved", reportRepository.countByStatus("RESOLVED")
        ));
    }
}
