package com.example.student.service;

import com.example.student.dto.ConceptDTO;
import com.example.student.dto.ConceptDetailDTO;
import com.example.student.dto.ConceptSearchDTO;
import com.example.student.exception.ResourceNotFoundException;
import com.example.student.model.Concept;
import com.example.student.repository.ConceptRepository;
import com.example.student.repository.UserConceptProgressRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConceptService {

    private final ConceptRepository conceptRepository;
    private final UserConceptProgressRepository progressRepository;

    public ConceptService(ConceptRepository conceptRepository,
                          UserConceptProgressRepository progressRepository) {
        this.conceptRepository = conceptRepository;
        this.progressRepository = progressRepository;
    }

    public List<ConceptDTO> getConceptsForSubject(String subjectId, String userId) {
        return conceptRepository.findBySubjectIdOrderByOrderIndex(subjectId)
                .stream().map(c -> toDTO(c, userId)).collect(Collectors.toList());
    }

    public ConceptDetailDTO getConceptDetail(String conceptId, String userId) {
        Concept concept = conceptRepository.findById(conceptId)
                .orElseThrow(() -> new ResourceNotFoundException("Concept not found"));

        List<Concept> siblings = conceptRepository.findBySubjectIdOrderByOrderIndex(concept.getSubjectId());

        int currentIdx = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).getId().equals(conceptId)) {
                currentIdx = i;
                break;
            }
        }

        ConceptDetailDTO.NavItem prev = currentIdx > 0
                ? new ConceptDetailDTO.NavItem(
                        siblings.get(currentIdx - 1).getId(),
                        siblings.get(currentIdx - 1).getTitle())
                : null;
        ConceptDetailDTO.NavItem next = currentIdx < siblings.size() - 1
                ? new ConceptDetailDTO.NavItem(
                        siblings.get(currentIdx + 1).getId(),
                        siblings.get(currentIdx + 1).getTitle())
                : null;

        boolean done = progressRepository.existsByUserIdAndConceptId(userId, conceptId);

        ConceptDetailDTO dto = new ConceptDetailDTO();
        dto.setId(concept.getId());
        dto.setTitle(concept.getTitle());
        dto.setWhatItIs(concept.getWhatItIs());
        dto.setWhyItMatters(concept.getWhyItMatters());
        dto.setCodeExample(concept.getCodeExample());
        dto.setIntroduction(concept.getIntroduction());
        dto.setExplanationSimple(concept.getExplanationSimple());
        dto.setExplanationTechnical(concept.getExplanationTechnical());
        dto.setSyntax(concept.getSyntax());
        dto.setExamples(concept.getExamples());
        dto.setKeyPoints(concept.getKeyPoints());
        dto.setTip(concept.getTip());
        dto.setCommonMistakes(concept.getCommonMistakes());
        dto.setRank(concept.getRank() != null ? concept.getRank() : "E");
        dto.setOrderIndex(concept.getOrderIndex());
        dto.setEstimatedMinutes(concept.getEstimatedMinutes());
        dto.setCompleted(done);
        dto.setSubjectId(concept.getSubjectId());
        dto.setSubjectTitle(concept.getSubjectTitle());
        dto.setTotalInSubject(siblings.size());
        dto.setPrevConcept(prev);
        dto.setNextConcept(next);
        return dto;
    }

    public List<ConceptSearchDTO> search(String query) {
        return conceptRepository.findByTitleContainingIgnoreCase(query)
                .stream().map(c -> new ConceptSearchDTO(
                        c.getId(), c.getTitle(),
                        c.getSubjectId(),
                        c.getSubjectTitle(),
                        c.getSubjectIcon()
                )).collect(Collectors.toList());
    }

    private ConceptDTO toDTO(Concept c, String userId) {
        boolean done = progressRepository.existsByUserIdAndConceptId(userId, c.getId());
        ConceptDTO dto = new ConceptDTO();
        dto.setId(c.getId());
        dto.setTitle(c.getTitle());
        dto.setWhatItIs(c.getWhatItIs());
        dto.setWhyItMatters(c.getWhyItMatters());
        dto.setCodeExample(c.getCodeExample());
        dto.setIntroduction(c.getIntroduction());
        dto.setExplanationSimple(c.getExplanationSimple());
        dto.setExplanationTechnical(c.getExplanationTechnical());
        dto.setSyntax(c.getSyntax());
        dto.setExamples(c.getExamples());
        dto.setKeyPoints(c.getKeyPoints());
        dto.setTip(c.getTip());
        dto.setCommonMistakes(c.getCommonMistakes());
        dto.setRank(c.getRank() != null ? c.getRank() : "E");
        dto.setOrderIndex(c.getOrderIndex());
        dto.setEstimatedMinutes(c.getEstimatedMinutes());
        dto.setCompleted(done);
        return dto;
    }
}
