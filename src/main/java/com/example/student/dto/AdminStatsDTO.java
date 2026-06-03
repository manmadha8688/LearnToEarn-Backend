package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @AllArgsConstructor @NoArgsConstructor
public class AdminStatsDTO {
    private long totalUsers;
    private long totalStudents;
    private long totalGuests;
    private long totalSubjects;
    private long totalConcepts;
    private long totalRoadmaps;
    private List<Map<String, Object>> recentUsers;
    private List<Map<String, Object>> topSubjects;
}
