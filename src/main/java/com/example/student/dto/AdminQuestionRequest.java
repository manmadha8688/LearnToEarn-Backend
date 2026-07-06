package com.example.student.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AdminQuestionRequest {
    private String conceptId;
    private String subjectId;

    @NotBlank
    @Size(max = 2000)
    private String text;

    @NotEmpty
    @Size(min = 2, max = 8)
    private List<@NotBlank @Size(max = 1000) String> options;

    @Min(0)
    private int correctIndex;

    @Size(max = 4000)
    private String explanation;

    @Size(max = 50)
    private String difficulty;
}
