package com.example.student.config;

import com.example.student.model.Concept;
import com.example.student.model.ProblemQuestion;
import com.example.student.model.Question;
import com.example.student.model.Roadmap;
import com.example.student.model.User;
import com.example.student.repository.ConceptRepository;
import com.example.student.repository.ProblemRepository;
import com.example.student.repository.QuestionRepository;
import com.example.student.repository.RoadmapRepository;
import com.example.student.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProblemRepository problemRepository;
    private final RoadmapRepository roadmapRepository;
    private final QuestionRepository questionRepository;
    private final ConceptRepository conceptRepository;
    private final Environment environment;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                      ProblemRepository problemRepository,
                      RoadmapRepository roadmapRepository,
                      QuestionRepository questionRepository,
                      ConceptRepository conceptRepository,
                      Environment environment) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.problemRepository = problemRepository;
        this.roadmapRepository = roadmapRepository;
        this.questionRepository = questionRepository;
        this.conceptRepository = conceptRepository;
        this.environment = environment;
    }


    @Override
    public void run(String... args) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            seedAdmin();
        } else {
            log.info("DataSeeder: skipping demo admin seed in prod profile");
        }
        cleanupLegacyGuests();
    }

    // ─── Guest Cleanup ───────────────────────────────────────────────────────
    // One-time migration: delete legacy guest accounts created before activity tracking
    private void cleanupLegacyGuests() {
        List<User> legacyGuests = userRepository.findByRole("GUEST").stream()
            .filter(u -> u.getLastLoginAt() == null)
            .collect(java.util.stream.Collectors.toList());
        if (!legacyGuests.isEmpty()) {
            userRepository.deleteAll(legacyGuests);
            log.info("DataSeeder: removed {} legacy guest account(s)", legacyGuests.size());
        }
    }

    // ─── Admin (local/dev only — skipped in prod profile) ────────────────────
    private void seedAdmin() {
        ensureAdmin("admin@demo.com", "Admin", "***REMOVED***");
        ensureAdmin("admin8688@gmail.com", "Main Admin", "***REMOVED***");
    }

    private void ensureAdmin(String email, String fullName, String rawPassword) {
        userRepository.findByEmail(email).ifPresentOrElse(
            u -> {
                boolean changed = false;
                if (!Boolean.TRUE.equals(u.getIsActive())) {
                    u.setIsActive(true);
                    changed = true;
                }
                if (!"ADMIN".equals(u.getRole())) {
                    u.setRole("ADMIN");
                    changed = true;
                }
                // Keep dev seed passwords in sync when they change (non-prod only).
                if (!passwordEncoder.matches(rawPassword, u.getPassword())) {
                    u.setPassword(passwordEncoder.encode(rawPassword));
                    changed = true;
                }
                if (changed) userRepository.save(u);
            },
            () -> {
                User a = new User();
                a.setFullName(fullName);
                a.setEmail(email);
                a.setPassword(passwordEncoder.encode(rawPassword));
                a.setRole("ADMIN");
                a.setProviders(new java.util.ArrayList<>(java.util.List.of("local")));
                a.setAvatarColor("#4F46E5");
                a.setIsActive(true);
                userRepository.save(a);
                log.info("DataSeeder: created admin {}", email);
            }
        );
    }

}
