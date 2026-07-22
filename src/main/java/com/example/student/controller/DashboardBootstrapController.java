package com.example.student.controller;

import com.example.student.model.User;
import com.example.student.service.DashboardBootstrapService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardBootstrapController {

    private final DashboardBootstrapService dashboardBootstrapService;

    public DashboardBootstrapController(DashboardBootstrapService dashboardBootstrapService) {
        this.dashboardBootstrapService = dashboardBootstrapService;
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(dashboardBootstrapService.getBootstrap(user));
    }
}
