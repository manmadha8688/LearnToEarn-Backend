package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @AllArgsConstructor @NoArgsConstructor
public class SubjectCompletionAgg {
    private String subjectId;
    private String subjectTitle;
    private String subjectIcon;
    private long count;
}
