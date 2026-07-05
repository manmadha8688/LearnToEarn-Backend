package com.example.student.config;

import com.example.student.model.User;
import com.example.student.repository.UserRepository;
import com.example.student.service.AdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class GuestCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(GuestCleanupScheduler.class);

    private final UserRepository userRepository;
    private final AdminService adminService;

    public GuestCleanupScheduler(UserRepository userRepository, AdminService adminService) {
        this.userRepository = userRepository;
        this.adminService = adminService;
    }

    // Runs every day at 2:00 AM IST — deletes GUEST accounts older than 3 days
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Kolkata")
    public void cleanupExpiredGuests() {
        LocalDateTime cutoff = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).minusDays(3);
        List<User> expired = userRepository.findByRoleAndCreatedAtBefore("GUEST", cutoff);
        if (!expired.isEmpty()) {
            // Remove each guest's learning data first so we don't orphan progress/attempts/badges.
            expired.forEach(g -> adminService.deleteUserOwnedData(g.getId()));
            userRepository.deleteAll(expired);
            log.info("GuestCleanup: deleted {} expired guest account(s) and their learning data", expired.size());
        }
    }
}
