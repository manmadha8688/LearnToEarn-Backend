package com.example.student.service;

import com.example.student.dto.RoadmapDetailDTO;
import com.example.student.dto.RoadmapListDTO;
import com.example.student.exception.ResourceNotFoundException;
import com.example.student.model.*;
import com.example.student.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoadmapService {

    private final RoadmapRepository roadmapRepository;
    private final RoadmapSubjectRepository roadmapSubjectRepository;
    private final ConceptRepository conceptRepository;
    private final UserConceptProgressRepository progressRepository;
    private final UserRoadmapEnrollmentRepository enrollmentRepository;
    private final UserSubjectBadgeRepository badgeRepository;

    public RoadmapService(RoadmapRepository roadmapRepository,
                          RoadmapSubjectRepository roadmapSubjectRepository,
                          ConceptRepository conceptRepository,
                          UserConceptProgressRepository progressRepository,
                          UserRoadmapEnrollmentRepository enrollmentRepository,
                          UserSubjectBadgeRepository badgeRepository) {
        this.roadmapRepository = roadmapRepository;
        this.roadmapSubjectRepository = roadmapSubjectRepository;
        this.conceptRepository = conceptRepository;
        this.progressRepository = progressRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.badgeRepository = badgeRepository;
    }

    public List<RoadmapListDTO> getAllRoadmaps(String userId) {
        return roadmapRepository.findByIsPublishedTrue().stream().map(r -> {
            List<RoadmapSubject> roadmapSubjects =
                    roadmapSubjectRepository.findByRoadmapIdOrderByOrderIndex(r.getId());
            int subjectCount = roadmapSubjects.size();
            java.util.Optional<UserRoadmapEnrollment> enr =
                    enrollmentRepository.findByUserIdAndRoadmapId(userId, r.getId());
            boolean enrolled = enr.isPresent();
            boolean paused   = enr.map(UserRoadmapEnrollment::isPaused).orElse(false);
            boolean allSubjectsDone = enrolled && !roadmapSubjects.isEmpty() &&
                    roadmapSubjects.stream().allMatch(rs ->
                            badgeRepository.existsByUserIdAndSubjectId(userId, rs.getSubjectId()));
            return new RoadmapListDTO(r.getId(), r.getTitle(), r.getDescription(),
                    r.getRoleTarget(), r.getIcon(), r.getColor(),
                    r.getEstimatedWeeks(), subjectCount, enrolled, paused, allSubjectsDone);
        }).collect(Collectors.toList());
    }

    public RoadmapDetailDTO getRoadmapDetail(String roadmapId, String userId) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));

        List<RoadmapSubject> roadmapSubjects =
                roadmapSubjectRepository.findByRoadmapIdOrderByOrderIndex(roadmapId);

        List<RoadmapDetailDTO.SubjectProgress> subjects = roadmapSubjects.stream().map(rs -> {
            Subject s = rs.getSubject();
            long total = conceptRepository.countBySubjectId(s.getId());
            long completed = progressRepository.countByUserIdAndSubjectId(userId, s.getId());
            double pct = total > 0
                    ? Math.round((completed * 100.0 / total) * 10) / 10.0
                    : 0;
            boolean hasBadge = badgeRepository.existsByUserIdAndSubjectId(userId, s.getId());
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

        return new RoadmapDetailDTO(roadmap.getId(), roadmap.getTitle(), roadmap.getDescription(),
                roadmap.getIcon(), roadmap.getColor(), subjects, totalSubjects, completedSubjects,
                Math.round(overall * 10) / 10.0, enrolled, paused);
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
