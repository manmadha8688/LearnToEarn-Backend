package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @AllArgsConstructor @NoArgsConstructor
public class DashboardBootstrapDTO {
    private ProgressSummaryDTO progressSummary;
    private Map<String, Object> quests;
    private List<Map<String, Object>> quizHistory;
    private List<RoadmapListDTO> roadmaps;
}
