package com.example.student.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter @Setter @NoArgsConstructor
public class AptitudeMockSubmitDTO {
    /** questionId → chosen letter (A–D). Unanswered ids may be omitted. */
    private Map<String, String> answers;
    /** Same order as the paper sections — for review grouping. */
    private List<String> questionIds;
}
