package com.example.student.service;

import com.example.student.dto.RoadmapDetailDTO;
import com.example.student.dto.RoadmapListDTO;
import com.example.student.exception.ResourceNotFoundException;
import com.example.student.model.*;
import com.example.student.repository.*;
import com.example.student.service.CacheService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RoadmapService {

    private final RoadmapRepository roadmapRepository;
    private final RoadmapSubjectRepository roadmapSubjectRepository;
    private final ConceptRepository conceptRepository;
    private final UserConceptProgressRepository progressRepository;
    private final UserRoadmapEnrollmentRepository enrollmentRepository;
    private final UserSubjectBadgeRepository badgeRepository;
    private final CacheService cacheService;

    public RoadmapService(RoadmapRepository roadmapRepository,
                          RoadmapSubjectRepository roadmapSubjectRepository,
                          ConceptRepository conceptRepository,
                          UserConceptProgressRepository progressRepository,
                          UserRoadmapEnrollmentRepository enrollmentRepository,
                          UserSubjectBadgeRepository badgeRepository,
                          CacheService cacheService) {
        this.roadmapRepository = roadmapRepository;
        this.roadmapSubjectRepository = roadmapSubjectRepository;
        this.conceptRepository = conceptRepository;
        this.progressRepository = progressRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.badgeRepository = badgeRepository;
        this.cacheService = cacheService;
    }

    public List<RoadmapListDTO> getAllRoadmaps(String userId) {
        // Load static data from cache (set by CacheWarmup + evicted on admin mutations)
        List<Roadmap> roadmaps = cacheService.get("roadmaps", "published",
                roadmapRepository::findByIsPublishedTrue);
        if (roadmaps == null) roadmaps = roadmapRepository.findByIsPublishedTrue();

        // 2 bulk queries for user-specific data instead of N + N×M individual queries
        Map<String, UserRoadmapEnrollment> enrollmentMap = enrollmentRepository
                .findByUserId(userId).stream()
                .collect(Collectors.toMap(UserRoadmapEnrollment::getRoadmapId, e -> e));

        Set<String> badgedSubjectIds = badgeRepository.findByUserId(userId).stream()
                .map(UserSubjectBadge::getSubjectId)
                .collect(Collectors.toSet());

        return roadmaps.stream().map(r -> {
            // Roadmap subjects — cached (static, only changes when admin edits)
            List<RoadmapSubject> roadmapSubjects = cacheService.get(
                    "roadmaps", "subjects:" + r.getId(),
                    () -> roadmapSubjectRepository.findByRoadmapIdOrderByOrderIndex(r.getId()));
            if (roadmapSubjects == null) roadmapSubjects = List.of();

            int subjectCount = roadmapSubjects.size();
            UserRoadmapEnrollment enr = enrollmentMap.get(r.getId());
            boolean enrolled = enr != null;
            boolean paused   = enr != null && enr.isPaused();
            boolean allSubjectsDone = enrolled && subjectCount > 0 &&
                    roadmapSubjects.stream().allMatch(rs ->
                            badgedSubjectIds.contains(rs.getSubjectId()));

            RoadmapListDTO dto = new RoadmapListDTO();
            dto.setId(r.getId());
            dto.setTitle(r.getTitle());
            dto.setDescription(r.getDescription());
            dto.setRoleTarget(r.getRoleTarget());
            dto.setIcon(r.getIcon());
            dto.setColor(r.getColor());
            dto.setEstimatedWeeks(r.getEstimatedWeeks());
            dto.setSubjectCount(subjectCount);
            dto.setEnrolled(enrolled);
            dto.setPaused(paused);
            dto.setAllSubjectsDone(allSubjectsDone);
            dto.setRoleTargets(r.getRoleTargets());
            dto.setOverview(r.getOverview());
            dto.setWhyLearn(r.getWhyLearn());
            dto.setForWho(r.getForWho());
            dto.setPrerequisites(r.getPrerequisites());
            dto.setToolsRequired(r.getToolsRequired());
            dto.setOutcomes(r.getOutcomes());
            return dto;
        }).collect(Collectors.toList());
    }

    public RoadmapDetailDTO getRoadmapDetail(String roadmapId, String userId) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));

        List<RoadmapSubject> roadmapSubjects =
                roadmapSubjectRepository.findByRoadmapIdOrderByOrderIndex(roadmapId);

        List<String> subjectIds = roadmapSubjects.stream()
                .map(rs -> rs.getSubject().getId())
                .collect(Collectors.toList());

        Map<String, Long> completedBySubject = progressRepository.findByUserId(userId).stream()
                .filter(p -> subjectIds.contains(p.getSubjectId()))
                .collect(Collectors.groupingBy(UserConceptProgress::getSubjectId, Collectors.counting()));

        Set<String> badgedSubjectIds = badgeRepository.findByUserId(userId).stream()
                .map(UserSubjectBadge::getSubjectId)
                .filter(subjectIds::contains)
                .collect(Collectors.toSet());

        List<RoadmapDetailDTO.SubjectProgress> subjects = roadmapSubjects.stream().map(rs -> {
            Subject s = rs.getSubject();
            long total = cacheService.get("concepts", "count:" + s.getId(),
                    () -> conceptRepository.countBySubjectId(s.getId()));
            long completed = completedBySubject.getOrDefault(s.getId(), 0L);
            double pct = total > 0
                    ? Math.round((completed * 100.0 / total) * 10) / 10.0
                    : 0;
            boolean hasBadge = badgedSubjectIds.contains(s.getId());
            return new RoadmapDetailDTO.SubjectProgress(
                    s.getId(), s.getTitle(), s.getIcon(), s.getColor(),
                    rs.getOrderIndex(), (int) total, completed, pct, hasBadge);
        }).collect(Collectors.toList());

        int totalSubjects = subjects.size();
        int completedSubjects = (int) subjects.stream()
                .filter(RoadmapDetailDTO.SubjectProgress::isHasBadge).count();
        double overall = totalSubjects > 0
                ? subjects.stream()
                        .mapToDouble(RoadmapDetailDTO.SubjectProgress::getPercentage)
                        .average().orElse(0)
                : 0;
        java.util.Optional<UserRoadmapEnrollment> enr =
                enrollmentRepository.findByUserIdAndRoadmapId(userId, roadmapId);
        boolean enrolled = enr.isPresent();
        boolean paused   = enr.map(UserRoadmapEnrollment::isPaused).orElse(false);

        RoadmapDetailDTO dto = new RoadmapDetailDTO();
        dto.setId(roadmap.getId());
        dto.setTitle(roadmap.getTitle());
        dto.setDescription(roadmap.getDescription());
        dto.setIcon(roadmap.getIcon());
        dto.setColor(roadmap.getColor());
        dto.setSubjects(subjects);
        dto.setTotalSubjects(totalSubjects);
        dto.setCompletedSubjects(completedSubjects);
        dto.setOverallPercentage(Math.round(overall * 10) / 10.0);
        dto.setEnrolled(enrolled);
        dto.setPaused(paused);
        dto.setEstimatedWeeks(roadmap.getEstimatedWeeks());
        dto.setRoleTarget(roadmap.getRoleTarget());
        // Multiple roles + Rich info
        dto.setRoleTargets(roadmap.getRoleTargets());
        dto.setOverview(roadmap.getOverview());
        dto.setWhyLearn(roadmap.getWhyLearn());
        dto.setForWho(roadmap.getForWho());
        dto.setPrerequisites(roadmap.getPrerequisites());
        dto.setToolsRequired(roadmap.getToolsRequired());
        dto.setOutcomes(roadmap.getOutcomes());
        return dto;
    }

    public void enroll(String roadmapId, String userId) {
        if (enrollmentRepository.existsByUserIdAndRoadmapId(userId, roadmapId)) return;
        if (!roadmapRepository.existsById(roadmapId))
            throw new ResourceNotFoundException("Roadmap not found");

        UserRoadmapEnrollment enrollment = new UserRoadmapEnrollment();
        enrollment.setUserId(userId);
        enrollment.setRoadmapId(roadmapId);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollmentRepository.save(enrollment);
    }

    public void pauseHunt(String roadmapId, String userId) {
        enrollmentRepository.findByUserIdAndRoadmapId(userId, roadmapId).ifPresent(e -> {
            e.setPaused(true);
            enrollmentRepository.save(e);
        });
    }

    public void resumeHunt(String roadmapId, String userId) {
        enrollmentRepository.findByUserIdAndRoadmapId(userId, roadmapId).ifPresent(e -> {
            e.setPaused(false);
            enrollmentRepository.save(e);
        });
    }

    public List<RoadmapDetailDTO> getEnrolledRoadmaps(String userId) {
        return enrollmentRepository.findByUserId(userId).stream()
                .map(e -> getRoadmapDetail(e.getRoadmapId(), userId))
                .collect(Collectors.toList());
    }
}
