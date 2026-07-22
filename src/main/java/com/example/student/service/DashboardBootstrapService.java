package com.example.student.service;

import com.example.student.dto.DashboardBootstrapDTO;
import com.example.student.dto.ProgressSummaryDTO;
import com.example.student.dto.RoadmapListDTO;
import com.example.student.model.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class DashboardBootstrapService {

    private final ProgressService progressService;
    private final QuestService questService;
    private final QuizService quizService;
    private final RoadmapService roadmapService;
    private final CacheService cacheService;

    public DashboardBootstrapService(ProgressService progressService,
                                     QuestService questService,
                                     QuizService quizService,
                                     RoadmapService roadmapService,
                                     CacheService cacheService) {
        this.progressService = progressService;
        this.questService = questService;
        this.quizService = quizService;
        this.roadmapService = roadmapService;
        this.cacheService = cacheService;
    }

    public DashboardBootstrapDTO getBootstrap(User user) {
        String userId = user.getId();
        return cacheService.get("dashboardBootstrap", userId, () -> buildBootstrap(user));
    }

    public void evict(String userId) {
        if (userId != null) cacheService.evict("dashboardBootstrap", userId);
    }

    private DashboardBootstrapDTO buildBootstrap(User user) {
        String userId = user.getId();

        CompletableFuture<ProgressSummaryDTO> summaryFuture =
                CompletableFuture.supplyAsync(() -> progressService.getProgressSummary(user));
        CompletableFuture<Map<String, Object>> questsFuture =
                CompletableFuture.supplyAsync(() -> questService.getQuests(userId));
        CompletableFuture<List<Map<String, Object>>> historyFuture =
                CompletableFuture.supplyAsync(() -> quizService.getQuizHistory(userId, 5));
        CompletableFuture<List<RoadmapListDTO>> roadmapsFuture =
                CompletableFuture.supplyAsync(() -> roadmapService.getAllRoadmaps(userId));

        CompletableFuture.allOf(summaryFuture, questsFuture, historyFuture, roadmapsFuture).join();

        DashboardBootstrapDTO dto = new DashboardBootstrapDTO();
        dto.setProgressSummary(summaryFuture.join());
        dto.setQuests(questsFuture.join());
        dto.setQuizHistory(historyFuture.join());
        dto.setRoadmaps(roadmapsFuture.join());
        return dto;
    }
}
