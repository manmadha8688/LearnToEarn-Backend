package com.example.student.service;

import com.example.student.exception.ResourceNotFoundException;
import com.example.student.model.Certificate;
import com.example.student.model.Roadmap;
import com.example.student.model.Subject;
import com.example.student.model.User;
import com.example.student.repository.CertificateRepository;
import com.example.student.repository.RoadmapRepository;
import com.example.student.repository.SubjectRepository;
import com.example.student.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and serves verifiable student certificates. A certificate is minted automatically
 * when a subject gate is cleared (Subject Mastery) or a career path is completed
 * (Career Path Completion). All display fields are snapshotted at issue time.
 */
@Service
public class CertificateService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CertificateRepository certRepo;
    private final SubjectRepository subjectRepo;
    private final RoadmapRepository roadmapRepo;
    private final UserRepository userRepo;
    private final CacheService cacheService;

    public CertificateService(CertificateRepository certRepo,
                              SubjectRepository subjectRepo,
                              RoadmapRepository roadmapRepo,
                              UserRepository userRepo,
                              CacheService cacheService) {
        this.certRepo = certRepo;
        this.subjectRepo = subjectRepo;
        this.roadmapRepo = roadmapRepo;
        this.userRepo = userRepo;
        this.cacheService = cacheService;
    }

    // ── Issue (called from QuizService on a passing subject/roadmap attempt) ─────

    public Certificate issueSubjectCertificate(String userId, String subjectId, int score, int total) {
        Subject s = subjectRepo.findById(subjectId).orElse(null);
        String title = s != null ? s.getTitle() : "Subject";
        String icon  = s != null && s.getIcon()  != null ? s.getIcon()  : "📚";
        String color = s != null && s.getColor() != null ? s.getColor() : "#9B6ED4";
        return upsert(userId, "SUBJECT", subjectId, title, "Subject Mastery",
                "SUBJECT_MASTERED", score, total, icon, color);
    }

    public Certificate issueRoadmapCertificate(String userId, String roadmapId, String badge, int score, int total) {
        Roadmap r = roadmapRepo.findById(roadmapId).orElse(null);
        String title = r != null
                ? (r.getRoleTarget() != null && !r.getRoleTarget().isBlank() ? r.getRoleTarget() : r.getTitle())
                : "Career Path";
        String icon  = r != null && r.getIcon()  != null ? r.getIcon()  : "🗺️";
        String color = r != null && r.getColor() != null ? r.getColor() : "#7C3AED";
        return upsert(userId, "ROADMAP", roadmapId, title, "Career Path Completion",
                badge, score, total, icon, color);
    }

    private Certificate upsert(String userId, String type, String refId, String credentialTitle,
                               String credentialKind, String badge, int score, int total,
                               String icon, String color) {
        int pct = total > 0 ? (int) Math.round(score * 100.0 / total) : 0;
        User u = userRepo.findById(userId).orElse(null);
        String recipient = u != null && u.getFullName() != null ? u.getFullName() : "Student";
        String rank = u != null && u.getRank() != null ? u.getRank() : "E";
        LocalDateTime now = LocalDateTime.now(IST);

        Certificate c = certRepo.findByUserIdAndTypeAndRefId(userId, type, refId).orElse(null);
        if (c == null) {
            c = Certificate.builder()
                    .code(generateCode())
                    .userId(userId)
                    .recipientName(recipient)
                    .type(type)
                    .refId(refId)
                    .credentialTitle(credentialTitle)
                    .credentialKind(credentialKind)
                    .badge(badge)
                    .score(score)
                    .total(total)
                    .scorePercent(pct)
                    .icon(icon)
                    .color(color)
                    .rankAtIssue(rank)
                    .issuedAt(now)
                    .updatedAt(now)
                    .build();
        } else {
            // Keep the original issue date; only lift the score snapshot if they did better.
            if (score > c.getScore()) {
                c.setScore(score);
                c.setTotal(total);
                c.setScorePercent(pct);
                c.setBadge(badge);
            }
            c.setRecipientName(recipient);
            c.setCredentialTitle(credentialTitle);
            c.setIcon(icon);
            c.setColor(color);
            c.setUpdatedAt(now);
        }
        Certificate saved = certRepo.save(c);
        cacheService.evict("certificates", "user:" + userId);
        return saved;
    }

    // ── Read ────────────────────────────────────────────────────────────────────

    public List<Certificate> getMyCertificates(String userId) {
        return cacheService.get("certificates", "user:" + userId,
                () -> certRepo.findByUserIdOrderByIssuedAtDesc(userId));
    }

    public Certificate getOwned(String id, String userId) {
        Certificate c = certRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found"));
        if (!c.getUserId().equals(userId)) throw new RuntimeException("Access denied");
        return c;
    }

    /** Public verification — returns non-sensitive fields only (never the userId). */
    public Map<String, Object> verify(String code) {
        Certificate c = certRepo.findByCode(code == null ? "" : code.trim().toUpperCase()).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        if (c == null) {
            m.put("valid", false);
            return m;
        }
        m.put("valid", true);
        m.put("code", c.getCode());
        m.put("recipientName", c.getRecipientName());
        m.put("credentialTitle", c.getCredentialTitle());
        m.put("credentialKind", c.getCredentialKind());
        m.put("badge", c.getBadge());
        m.put("score", c.getScore());
        m.put("total", c.getTotal());
        m.put("scorePercent", c.getScorePercent());
        m.put("type", c.getType());
        m.put("icon", c.getIcon());
        m.put("color", c.getColor());
        m.put("rankAtIssue", c.getRankAtIssue());
        m.put("issuedAt", c.getIssuedAt() != null ? c.getIssuedAt().toString() : null);
        return m;
    }

    private String generateCode() {
        String code;
        do {
            code = "LFE-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        } while (certRepo.existsByCode(code));
        return code;
    }
}
