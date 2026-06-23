package com.example.student.config;

import com.example.student.model.User;
import com.example.student.repository.UserRepository;
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

    public GuestCleanupScheduler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Runs every day at 2:00 AM — deletes GUEST accounts older than 3 days
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredGuests() {
        LocalDateTime cutoff = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).minusDays(3);
        List<User> expired = userRepository.findByRoleAndCreatedAtBefore("GUEST", cutoff);
        if (!expired.isEmpty()) {
            userRepository.deleteAll(expired);
            log.info("GuestCleanup: deleted {} expired guest account(s)", expired.size());
        }
    }
}
