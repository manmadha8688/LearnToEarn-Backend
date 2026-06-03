package com.example.student.service;

import com.example.student.dto.ConceptDTO;
import com.example.student.dto.SubjectDTO;
import com.example.student.exception.ResourceNotFoundException;
import com.example.student.model.Concept;
import com.example.student.model.QuizAttempt;
import com.example.student.model.Subject;
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

    public SubjectService(SubjectRepository subjectRepository,
                          ConceptRepository conceptRepository,
                          UserConceptProgressRepository progressRepository,
                          QuizAttemptRepository attemptRepository) {
        this.subjectRepository = subjectRepository;
        this.conceptRepository = conceptRepository;
        this.progressRepository = progressRepository;
        this.attemptRepository = attemptRepository;
    }

    public List<SubjectDTO> getAllSubjects(String userId) {
        return subjectRepository.findAll().stream()
                .map(s -> toDTO(s, userId))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getSubjectDetail(String subjectId, String userId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));

        // Batch: completed concept IDs from UserConceptProgress (primary source)
        Set<String> completedByProgress = progressRepository.findByUserId(userId).stream()
                .filter(p -> subjectId.equals(p.getSubjectId()))
                .map(p -> p.getConceptId())
                .collect(Collectors.toSet());

        // Batch: concept IDs cleared via quiz (fallback — handles data inconsistency
        // where attempt.passed=true but UserConceptProgress entry was never created)
        Set<String> clearedByQuiz = attemptRepository.findByUserIdAndTypeAndPassedTrue(userId, "CONCEPT")
                .stream().map(QuizAttempt::getRefId).collect(Collectors.toSet());

        List<ConceptDTO> concepts = conceptRepository.findBySubjectIdOrderByOrderIndex(subjectId)
                .stream().map(c -> toConceptDTO(c, completedByProgress, clearedByQuiz))
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
        result.put("difficulty",       subject.getDifficulty());
        result.put("estimatedHours",   subject.getEstimatedHours());
        result.put("careerUse",        subject.getCareerUse());
        return result;
    }

    public List<SubjectDTO> search(String query, String userId) {
        return subjectRepository.findByTitleContainingIgnoreCase(query)
                .stream().map(s -> toDTO(s, userId)).collect(Collectors.toList());
    }

    private SubjectDTO toDTO(Subject s, String userId) {
        long total = conceptRepository.countBySubjectId(s.getId());
        long completed = progressRepository.countByUserIdAndSubjectId(userId, s.getId());
        SubjectDTO dto = new SubjectDTO();
        dto.setId(s.getId());
        dto.setTitle(s.getTitle());
        dto.setDescription(s.getDescription());
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
        dto.setWhatItIs(c.getWhatItIs());
        dto.setWhyItMatters(c.getWhyItMatters());
        dto.setCodeExample(c.getCodeExample());
        dto.setIntroduction(c.getIntroduction());
        dto.setRank(c.getRank() != null ? c.getRank() : "E");
        dto.setOrderIndex(c.getOrderIndex());
        dto.setEstimatedMinutes(c.getEstimatedMinutes());
        dto.setCompleted(done);
        return dto;
    }
}
