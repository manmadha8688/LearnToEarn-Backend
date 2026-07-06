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

    // ─── Admin ───────────────────────────────────────────────────────────────
    private void seedAdmin() {
        userRepository.findByEmail("admin@demo.com").ifPresentOrElse(
            u -> { if (!Boolean.TRUE.equals(u.getIsActive())) { u.setIsActive(true); userRepository.save(u); } },
            () -> {
                User a = new User();
                a.setFullName("Admin"); a.setEmail("admin@demo.com");
                a.setPassword(passwordEncoder.encode("***REMOVED***"));
                a.setRole("ADMIN");
                a.setAvatarColor("#4F46E5"); a.setIsActive(true);
                userRepository.save(a);
            }
        );
    }

}
