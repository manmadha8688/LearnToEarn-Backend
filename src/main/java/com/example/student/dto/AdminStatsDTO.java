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
    private long totalMissions;
    private long totalProblems;
    private long totalQuestions;
    private long totalReports;
    private long totalWalkIns;

    // ── Today's activity (IST) ──
    private long newUsersToday;
    private long loginsToday;

    // ── Reports breakdown ──
    private long openReports;

    // ── Auth provider split ──
    private long googleUsers;   // accounts with Google linked
    private long localUsers;    // accounts with an email/password login

    private List<Map<String, Object>> recentUsers;
    private List<Map<String, Object>> topSubjects;

    // Last 7 days: [{ date, logins, signups }] oldest → newest
    private List<Map<String, Object>> loginTrend;
}
