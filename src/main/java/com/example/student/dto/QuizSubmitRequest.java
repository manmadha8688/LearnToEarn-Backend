package com.example.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class QuizSubmitRequest {
    @NotBlank
    @Size(max = 50)
    private String type;

    @NotBlank
    @Size(max = 100)
    private String refId;

    @NotEmpty
    @Size(max = 200)
    private List<String> questionIds;

    @NotEmpty
    @Size(max = 200)
    private List<Integer> answers;
}
