package com.example.student.service;

import com.example.student.dto.*;
import com.example.student.exception.ResourceNotFoundException;
import com.example.student.model.*;
import com.example.student.repository.*;
import com.example.student.repository.MissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final ConceptRepository conceptRepository;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapSubjectRepository roadmapSubjectRepository;
    private final UserConceptProgressRepository progressRepository;
    private final QuestionRepository questionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final UserSubjectBadgeRepository badgeRepository;
    private final UserRoadmapBadgeRepository roadmapBadgeRepository;
    private final UserRoadmapEnrollmentRepository enrollmentRepository;
    private final CacheService cacheService;
    private final MissionRepository missionRepository;
    private final ProblemRepository problemRepository;
    private final ReportRepository reportRepository;
    private final WalkInRepository walkInRepository;
    private final BookmarkRepository bookmarkRepository;
    private final LoginEventRepository loginEventRepository;
    private final CertificateRepository certificateRepository;
    private final UserDailyQuestRepository dailyQuestRepository;

    public AdminService(UserRepository userRepository,
                        SubjectRepository subjectRepository,
                        ConceptRepository conceptRepository,
                        RoadmapRepository roadmapRepository,
                        RoadmapSubjectRepository roadmapSubjectRepository,
                        UserConceptProgressRepository progressRepository,
                        QuestionRepository questionRepository,
                        QuizAttemptRepository quizAttemptRepository,
                        UserSubjectBadgeRepository badgeRepository,
                        UserRoadmapBadgeRepository roadmapBadgeRepository,
                        UserRoadmapEnrollmentRepository enrollmentRepository,
                        CacheService cacheService,
                        MissionRepository missionRepository,
                        ProblemRepository problemRepository,
                        ReportRepository reportRepository,
                        WalkInRepository walkInRepository,
                        BookmarkRepository bookmarkRepository,
                        LoginEventRepository loginEventRepository,
                        CertificateRepository certificateRepository,
                        UserDailyQuestRepository dailyQuestRepository) {
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.conceptRepository = conceptRepository;
        this.roadmapRepository = roadmapRepository;
        this.roadmapSubjectRepository = roadmapSubjectRepository;
        this.progressRepository = progressRepository;
        this.questionRepository = questionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.badgeRepository = badgeRepository;
        this.roadmapBadgeRepository = roadmapBadgeRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.cacheService = cacheService;
        this.missionRepository = missionRepository;
        this.problemRepository = problemRepository;
        this.reportRepository = reportRepository;
        this.walkInRepository = walkInRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.loginEventRepository = loginEventRepository;
        this.certificateRepository = certificateRepository;
        this.dailyQuestRepository = dailyQuestRepository;
    }

    // ─── STATS ───────────────────────────────────────────────────────────────

    public AdminStatsDTO getStats() {
        List<Map<String, Object>> recentUsers = userRepository.findTop5ByOrderByCreatedAtDesc()
                .stream().map(u -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("fullName", u.getFullName());
                    m.put("email", u.getEmail());
                    m.put("role", u.getRole());
                    m.put("provider", deriveProvider(u));
                    m.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");
                    return m;
                }).collect(Collectors.toList());

        List<SubjectCompletionAgg> topRaw = progressRepository.findTopSubjectsByCompletion();
        List<Map<String, Object>> topSubjects = topRaw.stream()
                .map(r -> Map.<String, Object>of(
                        "subjectId", r.getSubjectId(),
                        "title", r.getSubjectTitle(),
                        "icon", r.getSubjectIcon(),
                        "completionCount", r.getCount()
                )).collect(Collectors.toList());

        // ── Today (IST) + last-7-day trend ──
        java.time.ZoneId ist = java.time.ZoneId.of("Asia/Kolkata");
        java.time.LocalDate today = java.time.LocalDate.now(ist);
        java.time.LocalDateTime startOfToday = today.atStartOfDay();

        long newUsersToday = userRepository.countByCreatedAtAfter(startOfToday);
        long loginsToday   = loginEventRepository.countByCreatedAtAfter(startOfToday);

        List<Map<String, Object>> loginTrend = new java.util.ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate day = today.minusDays(i);
            java.time.LocalDateTime dayStart = day.atStartOfDay();
            java.time.LocalDateTime dayEnd   = day.plusDays(1).atStartOfDay();
            Map<String, Object> point = new java.util.LinkedHashMap<>();
            point.put("date", day.toString());
            point.put("logins", loginEventRepository.countByCreatedAtBetween(dayStart, dayEnd));
            point.put("signups", userRepository.countByCreatedAtBetween(dayStart, dayEnd));
            loginTrend.add(point);
        }

        AdminStatsDTO dto = new AdminStatsDTO();
        dto.setTotalUsers(userRepository.count());
        dto.setTotalStudents(userRepository.countByRole("STUDENT"));
        dto.setTotalGuests(userRepository.countByRole("GUEST"));
        dto.setTotalSubjects(subjectRepository.count());
        dto.setTotalConcepts(conceptRepository.count());
        dto.setTotalRoadmaps(roadmapRepository.count());
        dto.setTotalMissions(missionRepository.count());
        dto.setTotalProblems(problemRepository.count());
        dto.setTotalQuestions(questionRepository.count());
        dto.setTotalReports(reportRepository.count());
        dto.setTotalWalkIns(walkInRepository.count());
        dto.setNewUsersToday(newUsersToday);
        dto.setLoginsToday(loginsToday);
        dto.setOpenReports(reportRepository.countByStatus("OPEN"));
        dto.setGoogleUsers(userRepository.countByProvidersContaining("google"));
        dto.setLocalUsers(userRepository.countByProvidersContaining("local"));
        dto.setRecentUsers(recentUsers);
        dto.setTopSubjects(topSubjects);
        dto.setLoginTrend(loginTrend);
        return dto;
    }

    /**
     * Human-friendly auth method for a user row:
     * "guest", "google", "email", or "email + google" when both are linked.
     */
    private String deriveProvider(User u) {
        if ("GUEST".equals(u.getRole())) return "guest";
        List<String> p = u.getProviders();
        boolean google = (p != null && p.contains("google")) || u.getGoogleId() != null;
        boolean local  = p != null && p.contains("local");
        if (google && local) return "email + google";
        if (google) return "google";
        return "email";
    }

    // ─── USERS ───────────────────────────────────────────────────────────────

    public Page<Map<String, Object>> getUsers(int page, int size, String search, String roleFilter) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        boolean hasSearch = search != null && !search.isBlank();
        String filter = roleFilter == null ? "" : roleFilter.trim().toLowerCase();

        Page<User> users;
        if ("guest".equals(filter)) {
            users = hasSearch
                    ? userRepository.findByRoleAndFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                            "GUEST", search, search, pageable)
                    : userRepository.findByRole("GUEST", pageable);
        } else if ("student".equals(filter) || "registered".equals(filter)) {
            users = hasSearch
                    ? userRepository.findByRoleAndFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                            "STUDENT", search, search, pageable)
                    : userRepository.findByRole("STUDENT", pageable);
        } else if ("admin".equals(filter)) {
            users = hasSearch
                    ? userRepository.findByRoleAndFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                            "ADMIN", search, search, pageable)
                    : userRepository.findByRole("ADMIN", pageable);
        } else {
            users = hasSearch
                    ? userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                            search, search, pageable)
                    : userRepository.findAll(pageable);
        }

        return users.map(u -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id",           u.getId());
            m.put("fullName",     u.getFullName());
            m.put("email",        u.getEmail());
            m.put("role",         u.getRole());
            m.put("provider",     deriveProvider(u));
            m.put("avatarColor",  u.getAvatarColor() != null ? u.getAvatarColor() : "#4F46E5");
            m.put("isActive",     u.getIsActive() != null ? u.getIsActive() : true);
            m.put("createdAt",    u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");
            m.put("lastLoginAt",  u.getLastLoginAt() != null ? u.getLastLoginAt().toString() : null);
            m.put("lastLogoutAt", u.getLastLogoutAt() != null ? u.getLastLogoutAt().toString() : null);
            m.put("loginCount",   u.getLoginCount());
            return m;
        });
    }

    public Map<String, Object> getUserProgress(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // One query for all of this user's progress, grouped in memory by subject
        // (replaces the previous N × countByUserIdAndSubjectId round-trips).
        Map<String, Long> completedBySubject = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.groupingBy(UserConceptProgress::getSubjectId, Collectors.counting()));

        List<Subject> subjects = cacheService.get("subjects", "all", subjectRepository::findAll);

        List<Map<String, Object>> subjectProgress = subjects.stream()
                .map(s -> {
                    // Concept counts served from Caffeine (warmed on startup) — no per-subject DB call.
                    long total = cacheService.getLong("concepts", "count:" + s.getId(),
                            () -> conceptRepository.countBySubjectId(s.getId()));
                    if (total == 0) return null;
                    long completed = completedBySubject.getOrDefault(s.getId(), 0L);
                    double pct = Math.round((completed * 100.0 / total) * 10) / 10.0;
                    return Map.<String, Object>of(
                            "subjectId", s.getId(), "title", s.getTitle(),
                            "icon", s.getIcon(), "total", (int) total,
                            "completed", completed, "percentage", pct);
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        return Map.of(
                "user", Map.of("id", user.getId(), "fullName", user.getFullName(),
                        "email", user.getEmail(), "role", user.getRole()),
                "subjectProgress", subjectProgress
        );
    }

    @Transactional
    public void deleteUser(String userId) {
        if (!userRepository.existsById(userId))
            throw new ResourceNotFoundException("User not found");
        deleteUserOwnedData(userId);
        userRepository.deleteById(userId);
    }

    /**
     * Remove all per-user learning data (progress, quiz attempts, badges, enrollments).
     * Reports, feedback and walk-ins are intentionally preserved as admin/community records.
     * Safe to call for any user id.
     */
    @Transactional
    public void deleteUserOwnedData(String userId) {
        progressRepository.deleteByUserId(userId);
        quizAttemptRepository.deleteByUserId(userId);
        badgeRepository.deleteByUserId(userId);
        roadmapBadgeRepository.deleteByUserId(userId);
        enrollmentRepository.deleteByUserId(userId);
        bookmarkRepository.deleteByUserId(userId);
        certificateRepository.deleteByUserId(userId);
        dailyQuestRepository.deleteByUserId(userId);
    }

    // ─── SUBJECTS ────────────────────────────────────────────────────────────

    public List<Subject> getAllSubjects() {
        return subjectRepository.findAll();
    }

    public Subject createSubject(AdminSubjectRequest req) {
        Subject s = new Subject();
        s.setTitle(req.getTitle());
        s.setDescription(req.getDescription());
        s.setIcon(req.getIcon() != null ? req.getIcon() : "📚");
        s.setColor(req.getColor() != null ? req.getColor() : "#4F46E5");
        s.setRank(req.getRank() != null ? req.getRank() : "E");
        s.setTotalConcepts(0);
        s.setOverview(req.getOverview());
        s.setWhyLearn(req.getWhyLearn());
        s.setForWho(req.getForWho());
        s.setPrerequisites(req.getPrerequisites());
        s.setOutcomes(req.getOutcomes());
        s.setWhatYouWillBuild(req.getWhatYouWillBuild());
        s.setToolsRequired(req.getToolsRequired());
        s.setDifficulty(req.getDifficulty());
        s.setEstimatedHours(req.getEstimatedHours());
        s.setCareerUse(req.getCareerUse());
        Subject saved = subjectRepository.save(s);
        cacheService.evictAll("subjects");
        return saved;
    }

    public Subject updateSubject(String id, AdminSubjectRequest req) {
        Subject s = subjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));
        if (req.getTitle() != null) s.setTitle(req.getTitle());
        if (req.getDescription() != null) s.setDescription(req.getDescription());
        if (req.getIcon() != null) s.setIcon(req.getIcon());
        if (req.getColor() != null) s.setColor(req.getColor());
        if (req.getRank() != null) s.setRank(req.getRank());
        if (req.getOverview() != null) s.setOverview(req.getOverview());
        if (req.getWhyLearn() != null) s.setWhyLearn(req.getWhyLearn());
        if (req.getForWho() != null) s.setForWho(req.getForWho());
        if (req.getPrerequisites() != null) s.setPrerequisites(req.getPrerequisites());
        if (req.getOutcomes() != null) s.setOutcomes(req.getOutcomes());
        if (req.getWhatYouWillBuild() != null) s.setWhatYouWillBuild(req.getWhatYouWillBuild());
        if (req.getToolsRequired() != null) s.setToolsRequired(req.getToolsRequired());
        if (req.getDifficulty() != null) s.setDifficulty(req.getDifficulty());
        if (req.getEstimatedHours() > 0) s.setEstimatedHours(req.getEstimatedHours());
        if (req.getCareerUse() != null) s.setCareerUse(req.getCareerUse());
        Subject saved = subjectRepository.save(s);
        cacheService.evict("subjects", "all");
        cacheService.evict("subjects", "id:" + id);
        cacheService.evict("concepts", "subject:" + id);
        cacheService.evict("concepts", "count:" + id);
        conceptRepository.findBySubjectIdOrderByOrderIndex(id).forEach(c ->
                cacheService.evict("concepts", "id:" + c.getId()));
        return saved;
    }

    @Transactional
    public void deleteSubject(String id) {
        if (!subjectRepository.existsById(id))
            throw new ResourceNotFoundException("Subject not found");

        // Collect concept IDs for bulk quiz-attempt cleanup
        List<String> conceptIds = conceptRepository.findBySubjectIdOrderByOrderIndex(id)
                .stream().map(c -> c.getId()).collect(Collectors.toList());

        if (!conceptIds.isEmpty()) {
            quizAttemptRepository.deleteByTypeAndRefIdIn("CONCEPT", conceptIds);
        }
        quizAttemptRepository.deleteByTypeAndRefId("SUBJECT", id);
        progressRepository.deleteBySubjectId(id);
        questionRepository.deleteBySubjectId(id);
        badgeRepository.deleteBySubjectId(id);
        certificateRepository.deleteByTypeAndRefId("SUBJECT", id);
        roadmapSubjectRepository.deleteBySubjectId(id);
        conceptRepository.deleteBySubjectId(id);
        subjectRepository.deleteById(id);
        cacheService.evictAll("subjects");
        cacheService.evictAll("concepts");
    }

    // ─── CONCEPTS ────────────────────────────────────────────────────────────

    public List<Concept> getConceptsBySubject(String subjectId) {
        return conceptRepository.findBySubjectIdOrderByOrderIndex(subjectId);
    }

    public Concept createConcept(AdminConceptRequest req) {
        Subject subject = subjectRepository.findById(req.getSubjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));

        int newIdx = req.getOrderIndex();
        if (newIdx > 0) {
            List<Concept> toShift = conceptRepository.findBySubjectIdOrderByOrderIndex(subject.getId())
                    .stream()
                    .filter(x -> x.getOrderIndex() >= newIdx)
                    .collect(Collectors.toList());
            toShift.forEach(x -> x.setOrderIndex(x.getOrderIndex() + 1));
            conceptRepository.saveAll(toShift);
        }

        Concept c = new Concept();
        c.setSubjectId(subject.getId());
        c.setSubjectTitle(subject.getTitle());
        c.setSubjectIcon(subject.getIcon());
        c.setTitle(req.getTitle());
        c.setWhatItIs(req.getWhatItIs());
        c.setWhyItMatters(req.getWhyItMatters());
        c.setCodeExample(req.getCodeExample());
        c.setIntroduction(req.getIntroduction());
        c.setExplanationSimple(req.getExplanationSimple());
        c.setExplanationTechnical(req.getExplanationTechnical());
        c.setSyntax(req.getSyntax());
        c.setExamples(req.getExamples());
        c.setKeyPoints(req.getKeyPoints());
        c.setTip(req.getTip());
        c.setCommonMistakes(req.getCommonMistakes());
        c.setTrickyProblems(req.getTrickyProblems());
        c.setRank(req.getRank() != null ? req.getRank() : "E");
        c.setEstimatedMinutes(req.getEstimatedMinutes() > 0 ? req.getEstimatedMinutes() : 15);
        c.setOrderIndex(newIdx);
        c.setVideoUrl(req.getVideoUrl());
        c.setVideoTitle(req.getVideoTitle());
        Concept saved = conceptRepository.save(c);
        subject.setTotalConcepts((int) conceptRepository.countBySubjectId(subject.getId()));
        subjectRepository.save(subject);
        cacheService.evict("concepts", "subject:" + subject.getId());
        cacheService.evict("concepts", "count:" + subject.getId());
        cacheService.evict("concepts", "total");
        cacheService.evict("subjects", "id:" + subject.getId());
        cacheService.evict("subjects", "all");
        return saved;
    }

    public Concept updateConcept(String id, AdminConceptRequest req) {
        Concept c = conceptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Concept not found"));

        if (req.getSubjectId() != null) c.setSubjectId(req.getSubjectId());
        if (req.getTitle() != null) c.setTitle(req.getTitle());
        if (req.getWhatItIs() != null) c.setWhatItIs(req.getWhatItIs());
        if (req.getWhyItMatters() != null) c.setWhyItMatters(req.getWhyItMatters());
        if (req.getCodeExample() != null) c.setCodeExample(req.getCodeExample());
        if (req.getIntroduction() != null) c.setIntroduction(req.getIntroduction());
        if (req.getExplanationSimple() != null) c.setExplanationSimple(req.getExplanationSimple());
        if (req.getExplanationTechnical() != null) c.setExplanationTechnical(req.getExplanationTechnical());
        if (req.getSyntax() != null) c.setSyntax(req.getSyntax());
        if (req.getExamples() != null) c.setExamples(req.getExamples());
        if (req.getKeyPoints() != null) c.setKeyPoints(req.getKeyPoints());
        if (req.getTip() != null) c.setTip(req.getTip());
        if (req.getCommonMistakes() != null) c.setCommonMistakes(req.getCommonMistakes());
        if (req.getTrickyProblems() != null) c.setTrickyProblems(req.getTrickyProblems());
        if (req.getRank() != null) c.setRank(req.getRank());
        if (req.getVideoUrl() != null) c.setVideoUrl(req.getVideoUrl());
        if (req.getVideoTitle() != null) c.setVideoTitle(req.getVideoTitle());
        if (req.getEstimatedMinutes() > 0) c.setEstimatedMinutes(req.getEstimatedMinutes());

        int newIdx = req.getOrderIndex();
        int oldIdx = c.getOrderIndex();
        if (newIdx > 0 && newIdx != oldIdx) {
            List<Concept> siblings = conceptRepository.findBySubjectIdOrderByOrderIndex(c.getSubjectId())
                    .stream()
                    .filter(s -> !s.getId().equals(id))
                    .collect(Collectors.toList());

            if (newIdx < oldIdx) {
                // Moving to a lower slot: push concepts between newIdx..oldIdx-1 down by 1
                siblings.stream()
                        .filter(s -> s.getOrderIndex() >= newIdx && s.getOrderIndex() < oldIdx)
                        .forEach(s -> s.setOrderIndex(s.getOrderIndex() + 1));
            } else {
                // Moving to a higher slot: pull concepts between oldIdx+1..newIdx up by 1
                siblings.stream()
                        .filter(s -> s.getOrderIndex() > oldIdx && s.getOrderIndex() <= newIdx)
                        .forEach(s -> s.setOrderIndex(s.getOrderIndex() - 1));
            }
            conceptRepository.saveAll(siblings);
            c.setOrderIndex(newIdx);
        }

        Concept updated = conceptRepository.save(c);
        cacheService.evict("concepts", "id:" + id);
        cacheService.evict("concepts", "subject:" + c.getSubjectId());
        // Concept summaries are embedded in the subject-detail payload, so refresh those too.
        cacheService.evict("subjects", "id:" + c.getSubjectId());
        cacheService.evict("subjects", "all");
        return updated;
    }

    @Transactional
    public void deleteConcept(String id) {
        Concept c = conceptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Concept not found"));
        String subjectId = c.getSubjectId();

        quizAttemptRepository.deleteByTypeAndRefId("CONCEPT", id);
        progressRepository.deleteByConceptId(id);
        questionRepository.deleteByConceptId(id);
        conceptRepository.deleteById(id);

        subjectRepository.findById(subjectId).ifPresent(subject -> {
            subject.setTotalConcepts((int) conceptRepository.countBySubjectId(subjectId));
            subjectRepository.save(subject);
        });
        cacheService.evict("concepts", "id:" + id);
        cacheService.evict("concepts", "subject:" + subjectId);
        cacheService.evict("concepts", "count:" + subjectId);
        cacheService.evict("concepts", "total");
        cacheService.evict("subjects", "id:" + subjectId);
        cacheService.evict("subjects", "all");
    }

    // ─── ROADMAPS ────────────────────────────────────────────────────────────

    public List<Roadmap> getAllRoadmaps() {
        return roadmapRepository.findAll();
    }

    public Roadmap createRoadmap(AdminRoadmapRequest req) {
        Roadmap r = new Roadmap();
        r.setTitle(req.getTitle());
        r.setDescription(req.getDescription());
        r.setRoleTarget(req.getRoleTarget());
        r.setIcon(req.getIcon() != null ? req.getIcon() : "🗺️");
        r.setColor(req.getColor() != null ? req.getColor() : "#7C3AED");
        r.setEstimatedWeeks(req.getEstimatedWeeks() > 0 ? req.getEstimatedWeeks() : 12);
        r.setPublished(true);
        r.setOverview(req.getOverview());
        r.setWhyLearn(req.getWhyLearn());
        r.setForWho(req.getForWho());
        r.setPrerequisites(req.getPrerequisites());
        r.setToolsRequired(req.getToolsRequired());
        r.setOutcomes(req.getOutcomes());
        r.setRoleTargets(req.getRoleTargets());
        Roadmap saved = roadmapRepository.save(r);
        cacheService.evictAll("roadmaps");
        return saved;
    }

    public Roadmap updateRoadmap(String id, AdminRoadmapRequest req) {
        Roadmap r = roadmapRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));
        if (req.getTitle() != null) r.setTitle(req.getTitle());
        if (req.getDescription() != null) r.setDescription(req.getDescription());
        if (req.getRoleTarget() != null) r.setRoleTarget(req.getRoleTarget());
        if (req.getIcon() != null) r.setIcon(req.getIcon());
        if (req.getColor() != null) r.setColor(req.getColor());
        if (req.getEstimatedWeeks() > 0) r.setEstimatedWeeks(req.getEstimatedWeeks());
        if (req.getOverview() != null)      r.setOverview(req.getOverview());
        if (req.getWhyLearn() != null)      r.setWhyLearn(req.getWhyLearn());
        if (req.getForWho() != null)        r.setForWho(req.getForWho());
        if (req.getPrerequisites() != null) r.setPrerequisites(req.getPrerequisites());
        if (req.getToolsRequired() != null) r.setToolsRequired(req.getToolsRequired());
        if (req.getOutcomes() != null)      r.setOutcomes(req.getOutcomes());
        if (req.getRoleTargets() != null)   r.setRoleTargets(req.getRoleTargets());
        Roadmap saved = roadmapRepository.save(r);
        cacheService.evictAll("roadmaps");
        return saved;
    }

    @Transactional
    public void deleteRoadmap(String id) {
        if (!roadmapRepository.existsById(id))
            throw new ResourceNotFoundException("Roadmap not found");
        quizAttemptRepository.deleteByTypeAndRefId("ROADMAP", id);
        roadmapBadgeRepository.deleteByRoadmapId(id);
        certificateRepository.deleteByTypeAndRefId("ROADMAP", id);
        enrollmentRepository.deleteByRoadmapId(id);
        roadmapSubjectRepository.deleteByRoadmapId(id);
        roadmapRepository.deleteById(id);
        cacheService.evictAll("roadmaps");
    }

    public List<RoadmapSubject> getRoadmapSubjects(String roadmapId) {
        return roadmapSubjectRepository.findByRoadmapIdOrderByOrderIndex(roadmapId);
    }

    public RoadmapSubject addSubjectToRoadmap(String roadmapId, String subjectId, int orderIndex) {
        if (!roadmapRepository.existsById(roadmapId))
            throw new ResourceNotFoundException("Roadmap not found");
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));

        if (orderIndex > 0) {
            List<RoadmapSubject> toShift = roadmapSubjectRepository.findByRoadmapIdOrderByOrderIndex(roadmapId)
                    .stream()
                    .filter(x -> x.getOrderIndex() >= orderIndex)
                    .collect(Collectors.toList());
            toShift.forEach(x -> x.setOrderIndex(x.getOrderIndex() + 1));
            roadmapSubjectRepository.saveAll(toShift);
        }

        RoadmapSubject rs = new RoadmapSubject();
        rs.setRoadmapId(roadmapId);
        rs.setSubjectId(subjectId);
        rs.setSubject(subject);
        rs.setOrderIndex(orderIndex);
        RoadmapSubject saved = roadmapSubjectRepository.save(rs);
        cacheService.evictAll("roadmaps");
        return saved;
    }

    public void removeSubjectFromRoadmap(String roadmapId, String subjectId) {
        roadmapSubjectRepository.deleteByRoadmapIdAndSubjectId(roadmapId, subjectId);
        cacheService.evictAll("roadmaps");
    }

    // ─── QUESTIONS ───────────────────────────────────────────────────────────

    public List<Question> getQuestionsByConceptId(String conceptId) {
        return questionRepository.findByConceptId(conceptId);
    }

    public Question createQuestion(AdminQuestionRequest req) {
        Question q = new Question();
        q.setConceptId(req.getConceptId());
        q.setSubjectId(req.getSubjectId());
        q.setText(req.getText());
        q.setOptions(req.getOptions());
        q.setCorrectIndex(req.getCorrectIndex());
        q.setExplanation(req.getExplanation());
        q.setDifficulty(req.getDifficulty() != null ? req.getDifficulty() : "MEDIUM");
        return questionRepository.save(q);
    }

    public Question updateQuestion(String id, AdminQuestionRequest req) {
        Question q = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));
        if (req.getText() != null) q.setText(req.getText());
        if (req.getOptions() != null) q.setOptions(req.getOptions());
        if (req.getCorrectIndex() >= 0) q.setCorrectIndex(req.getCorrectIndex());
        if (req.getExplanation() != null) q.setExplanation(req.getExplanation());
        if (req.getDifficulty() != null) q.setDifficulty(req.getDifficulty());
        return questionRepository.save(q);
    }

    public void deleteQuestion(String id) {
        if (!questionRepository.existsById(id))
            throw new ResourceNotFoundException("Question not found");
        questionRepository.deleteById(id);
    }

    // ─── MISSIONS ────────────────────────────────────────────────────────────

    public List<Mission> getAllMissions() {
        return missionRepository.findAllByOrderByOrderIndexAsc();
    }

    public Mission createMission(Mission mission) {
        Mission saved = missionRepository.save(mission);
        cacheService.evictAll("missions");
        return saved;
    }

    public Mission updateMission(String id, Mission updates) {
        Mission m = missionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mission not found"));
        m.setTitle(updates.getTitle());
        m.setMissionBrief(updates.getMissionBrief());
        m.setRank(updates.getRank());
        m.setCategory(updates.getCategory());
        m.setTargetRoles(updates.getTargetRoles());
        m.setTechStack(updates.getTechStack());
        m.setEstimatedHours(updates.getEstimatedHours());
        m.setSubjectIds(updates.getSubjectIds());
        m.setSubjectTitles(updates.getSubjectTitles());
        m.setObjectives(updates.getObjectives());
        m.setBonusObjectives(updates.getBonusObjectives());
        m.setHints(updates.getHints());
        m.setApproachSteps(updates.getApproachSteps());
        m.setLearningOutcome(updates.getLearningOutcome());
        m.setPrerequisites(updates.getPrerequisites());
        m.setConceptsCovered(updates.getConceptsCovered());
        m.setCommonMistakes(updates.getCommonMistakes());
        m.setPublished(updates.isPublished());
        m.setOrderIndex(updates.getOrderIndex());
        Mission saved = missionRepository.save(m);
        cacheService.evict("missions", "all");
        cacheService.evict("missions", "id:" + id);
        return saved;
    }

    // ─── PROBLEMS ────────────────────────────────────────────────────────────

    /** All Code Gym tracks — the single source of truth for cache warm/evict loops. */
    public static final List<String> PROBLEM_TRACKS = List.of(
            "START_CODING", "LOGIC_BUILDING", "SKILL_UP",
            "CRACK_IT", "BUILD_IT", "PROVE_IT");

    public List<ProblemQuestion> getAllProblems() {
        return problemRepository.findAllByOrderByOrderIndexAsc();
    }

    public ProblemQuestion createProblem(ProblemQuestion problem) {
        ProblemQuestion saved = problemRepository.save(problem);
        cacheService.evictAll("problems");
        return saved;
    }

    public ProblemQuestion updateProblem(String id, ProblemQuestion updates) {
        ProblemQuestion p = problemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found"));
        p.setTrack(updates.getTrack());
        p.setTopics(updates.getTopics());
        p.setCategory(updates.getCategory());
        p.setLevel(updates.getLevel());
        p.setTitle(updates.getTitle());
        p.setDescription(updates.getDescription());
        p.setInputFormat(updates.getInputFormat());
        p.setOutputFormat(updates.getOutputFormat());
        p.setConstraints(updates.getConstraints());
        p.setCodeSnippet(updates.getCodeSnippet());
        p.setExamples(updates.getExamples());
        p.setHints(updates.getHints());
        p.setApproach(updates.getApproach());
        p.setWhatYouLearn(updates.getWhatYouLearn());
        p.setSolutions(updates.getSolutions());
        p.setExplanation(updates.getExplanation());
        p.setTip(updates.getTip());
        p.setOrderIndex(updates.getOrderIndex());
        ProblemQuestion saved = problemRepository.save(p);
        cacheService.evict("problems", "all");
        for (String track : PROBLEM_TRACKS) cacheService.evict("problems", "track:" + track);
        cacheService.evict("problems", "id:" + id);
        return saved;
    }

    public void deleteProblem(String id) {
        if (!problemRepository.existsById(id))
            throw new ResourceNotFoundException("Problem not found");
        problemRepository.deleteById(id);
        cacheService.evictAll("problems");
    }

    public void deleteMission(String id) {
        if (!missionRepository.existsById(id))
            throw new ResourceNotFoundException("Mission not found");
        missionRepository.deleteById(id);
        cacheService.evictAll("missions");
    }

    public RoadmapSubject reorderSubjectInRoadmap(String roadmapId, String subjectId, int newOrderIndex) {
        RoadmapSubject rs = roadmapSubjectRepository.findByRoadmapIdAndSubjectId(roadmapId, subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Not found"));

        int oldIdx = rs.getOrderIndex();
        if (newOrderIndex > 0 && newOrderIndex != oldIdx) {
            List<RoadmapSubject> siblings = roadmapSubjectRepository.findByRoadmapIdOrderByOrderIndex(roadmapId)
                    .stream()
                    .filter(x -> !x.getSubjectId().equals(subjectId))
                    .collect(Collectors.toList());

            if (newOrderIndex < oldIdx) {
                siblings.stream()
                        .filter(x -> x.getOrderIndex() >= newOrderIndex && x.getOrderIndex() < oldIdx)
                        .forEach(x -> x.setOrderIndex(x.getOrderIndex() + 1));
            } else {
                siblings.stream()
                        .filter(x -> x.getOrderIndex() > oldIdx && x.getOrderIndex() <= newOrderIndex)
                        .forEach(x -> x.setOrderIndex(x.getOrderIndex() - 1));
            }
            roadmapSubjectRepository.saveAll(siblings);
            rs.setOrderIndex(newOrderIndex);
        }

        RoadmapSubject saved = roadmapSubjectRepository.save(rs);
        cacheService.evictAll("roadmaps");
        return saved;
    }
}
