package com.example.student.config;

import com.example.student.model.User;
import com.example.student.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedAdmin();
    }

    public void reconcileRichContent() {
        // no-op
    }

    private void seedAdmin() {
        userRepository.findByEmail("admin@demo.com").ifPresentOrElse(
            u -> { if (!Boolean.TRUE.equals(u.getIsActive())) { u.setIsActive(true); userRepository.save(u); } },
            () -> {
                User admin = new User();
                admin.setFullName("Admin");
                admin.setEmail("admin@demo.com");
                admin.setPassword(passwordEncoder.encode("***REMOVED***"));
                admin.setRole("ADMIN");
                admin.setCollegeName("Platform");
                admin.setAvatarColor("#4F46E5");
                admin.setIsActive(true);
                userRepository.save(admin);
            }
        );
    }
}
