package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @AllArgsConstructor @NoArgsConstructor
public class ConceptSearchDTO {
    private String id;
    private String title;
    private String subjectId;
    private String subjectTitle;
    private String subjectIcon;
}
