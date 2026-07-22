package com.example.student.service;

import com.example.student.dto.ConceptDTO;
import com.example.student.dto.SubjectListDTO;
import com.example.student.exception.ResourceNotFoundException;
import com.example.student.model.Concept;
import com.example.student.model.QuizAttempt;
import com.example.student.model.Subject;
import com.example.student.model.UserConceptProgress;
import com.example.student.repository.ConceptRepository;
import com.example.student.repository.QuizAttemptRepository;
import com.example.student.repository.SubjectRepository;
import com.example.student.repository.UserConceptProgressRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final ConceptRepository conceptRepository;
    private final UserConceptProgressRepository progressRepository;
    private final QuizAttemptRepository attemptRepository;
    private final CacheService cacheService;

    public SubjectService(SubjectRepository subjectRepository,
                          ConceptRepository conceptRepository,
                          UserConceptProgressRepository progressRepository,
                          QuizAttemptRepository attemptRepository,
                          CacheService cacheService) {
        this.subjectRepository = subjectRepository;
        this.conceptRepository = conceptRepository;
        this.progressRepository = progressRepository;
        this.attemptRepository = attemptRepository;
        this.cacheService = cacheService;
    }

    public List<SubjectListDTO> getAllSubjects(String userId) {
        List<Subject> subjects = cacheService.get("subjects", "all", subjectRepository::findAll);
        Map<String, Long> completedBySubject = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.groupingBy(UserConceptProgress::getSubjectId, Collectors.counting()));
        return subjects.stream()
                .map(s -> toListDTO(s, completedBySubject))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getSubjectDetail(String subjectId, String userId) {
        Subject subject = cacheService.get("subjects", "id:" + subjectId,
                () -> subjectRepository.findById(subjectId).orElse(null));
        if (subject == null) throw new ResourceNotFoundException("Subject not found");

        // User-specific progress — not cached (indexed, sub-ms, changes on concept completion).
        // Scoped query by (userId, subjectId) so Mongo returns only this subject's rows
        // instead of the whole user history filtered in memory.
        Set<String> completedByProgress = progressRepository.findByUserIdAndSubjectId(userId, subjectId).stream()
                .map(p -> p.getConceptId())
                .collect(Collectors.toSet());

        List<Concept> rawConcepts = cacheService.get("concepts", "subject:" + subjectId,
                () -> conceptRepository.findBySubjectIdOrderByOrderIndex(subjectId));

        List<String> conceptIds = rawConcepts.stream().map(Concept::getId).toList();
        Set<String> clearedByQuiz = conceptIds.isEmpty()
                ? java.util.Set.of()
                : attemptRepository.findByUserIdAndTypeAndPassedTrueAndRefIdIn(userId, "CONCEPT", conceptIds)
                        .stream().map(QuizAttempt::getRefId).collect(Collectors.toSet());

        List<ConceptDTO> concepts = rawConcepts.stream()
                .map(c -> toConceptDTO(c, completedByProgress, clearedByQuiz))
                .collect(Collectors.toList());

        long total = concepts.size();
        long completed = concepts.stream().filter(ConceptDTO::isCompleted).count();

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id",             subject.getId());
        result.put("title",          subject.getTitle());
        result.put("description",    subject.getDescription() != null ? subject.getDescription() : "");
        result.put("icon",           subject.getIcon());
        result.put("color",          subject.getColor());
        result.put("rank",           subject.getRank() != null ? subject.getRank() : "E");
        result.put("totalConcepts",  total);
        result.put("completedCount", completed);
        result.put("concepts",       concepts);
        // Rich info
        result.put("overview",         subject.getOverview());
        result.put("whyLearn",         subject.getWhyLearn());
        result.put("forWho",           subject.getForWho());
        result.put("prerequisites",    subject.getPrerequisites());
        result.put("outcomes",         subject.getOutcomes());
        result.put("whatYouWillBuild", subject.getWhatYouWillBuild());
        result.put("toolsRequired",    subject.getToolsRequired());
        result.put("difficulty",       subject.getDifficulty());
        result.put("estimatedHours",   subject.getEstimatedHours());
        result.put("careerUse",        subject.getCareerUse());
        return result;
    }

    public List<SubjectListDTO> search(String query, String userId) {
        Map<String, Long> completedBySubject = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.groupingBy(UserConceptProgress::getSubjectId, Collectors.counting()));
        return subjectRepository.findByTitleContainingIgnoreCase(query)
                .stream().map(s -> toListDTO(s, completedBySubject)).collect(Collectors.toList());
    }

    private SubjectListDTO toListDTO(Subject s, Map<String, Long> completedBySubject) {
        // Concept count is static — safe to cache; per-user progress batched above
        long total = cacheService.getLong("concepts", "count:" + s.getId(),
                () -> conceptRepository.countBySubjectId(s.getId()));
        long completed = completedBySubject.getOrDefault(s.getId(), 0L);
        SubjectListDTO dto = new SubjectListDTO();
        dto.setId(s.getId());
        dto.setTitle(s.getTitle());
        dto.setIcon(s.getIcon());
        dto.setColor(s.getColor());
        dto.setTotalConcepts((int) total);
        dto.setCompletedCount(completed);
        dto.setRank(s.getRank() != null ? s.getRank() : "E");
        dto.setOverview(s.getOverview());
        dto.setWhyLearn(s.getWhyLearn());
        dto.setForWho(s.getForWho());
        dto.setPrerequisites(s.getPrerequisites());
        dto.setOutcomes(s.getOutcomes());
        dto.setWhatYouWillBuild(s.getWhatYouWillBuild());
        dto.setToolsRequired(s.getToolsRequired());
        dto.setDifficulty(s.getDifficulty());
        dto.setEstimatedHours(s.getEstimatedHours());
        dto.setCareerUse(s.getCareerUse());
        return dto;
    }

    private ConceptDTO toConceptDTO(Concept c, Set<String> completedByProgress, Set<String> clearedByQuiz) {
        boolean done = completedByProgress.contains(c.getId()) || clearedByQuiz.contains(c.getId());
        ConceptDTO dto = new ConceptDTO();
        dto.setId(c.getId());
        dto.setTitle(c.getTitle());
        dto.setIntroduction(c.getIntroduction());
        dto.setRank(c.getRank() != null ? c.getRank() : "E");
        dto.setOrderIndex(c.getOrderIndex());
        dto.setEstimatedMinutes(c.getEstimatedMinutes());
        dto.setCompleted(done);
        dto.setVideoUrl(c.getVideoUrl());
        dto.setVideoTitle(c.getVideoTitle());
        return dto;
    }
}
