package com.example.student.controller;

import com.example.student.model.Report;
import com.example.student.model.User;
import com.example.student.repository.ReportRepository;
import com.example.student.security.ClientIpResolver;
import com.example.student.security.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportRepository reportRepository;
    private final RateLimiterService rateLimiter;

    public ReportController(ReportRepository reportRepository, RateLimiterService rateLimiter) {
        this.reportRepository = reportRepository;
        this.rateLimiter = rateLimiter;
    }

    // Student submits a report
    @PostMapping
    public ResponseEntity<?> submit(@Valid @RequestBody Report report,
                                    @AuthenticationPrincipal User user,
                                    HttpServletRequest request) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        long retryAfter = rateLimiter.hit("report", ClientIpResolver.resolve(request), 10, 3600);
        if (retryAfter > 0) {
            return ResponseEntity.status(429).body(Map.of(
                "error", "Too many submissions. Please try again later.",
                "retryAfter", retryAfter));
        }
        report.setId(null); // never let the client choose the document id (prevents overwriting others' reports)
        report.setUserId(user.getId());
        report.setUserEmail(user.getEmail());
        report.setUserName(user.getFullName());
        report.setStatus("OPEN");
        reportRepository.save(report);
        return ResponseEntity.ok(Map.of("message", "Report submitted. Thank you!"));
    }

    // Admin — list reports, optionally filtered by status
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable String id) {
        reportRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    // Stats for admin dashboard
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> stats() {
        return ResponseEntity.ok(Map.of(
            "total", reportRepository.count(),
            "open", reportRepository.countByStatus("OPEN"),
            "inProgress", reportRepository.countByStatus("IN_PROGRESS"),
            "resolved", reportRepository.countByStatus("RESOLVED")
        ));
    }
}
