package com.example.student.service;

import com.example.student.model.WalkIn;
import com.example.student.model.User;
import com.example.student.repository.WalkInRepository;
import com.example.student.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalkInService {

    private static final Logger log = LoggerFactory.getLogger(WalkInService.class);

    private final WalkInRepository walkInRepository;
    private final UserRepository userRepository;

    public List<WalkIn> getActiveWalkIns(String city) {
        // Auto-expire past walk-ins first
        expirePastWalkIns();

        List<WalkIn> result;
        if (city != null && !city.isBlank()) {
            result = walkInRepository.findByCityIgnoreCaseAndStatus(city, "ACTIVE");
        } else {
            result = walkInRepository.findByStatusOrderByWalkInDateAsc("ACTIVE");
        }
        return result;
    }

    public WalkIn createWalkIn(WalkIn walkIn, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        walkIn.setPostedBy(user.getFullName());
        walkIn.setPostedById(user.getId());
        walkIn.setCreatedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        walkIn.setStatus("ACTIVE");

        // Validate date is today or future
        try {
            LocalDate walkInDate = LocalDate.parse(walkIn.getWalkInDate());
            if (walkInDate.isBefore(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")))) {
                throw new RuntimeException("Walk-in date must be today or in the future");
            }
        } catch (java.time.format.DateTimeParseException e) {
            throw new RuntimeException("Invalid walk-in date format. Use YYYY-MM-DD");
        }

        return walkInRepository.save(walkIn);
    }

    public WalkIn updateWalkIn(String id, WalkIn updated) {
        WalkIn existing = walkInRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Walk-in not found"));
        if (updated.getCompanyName() != null) existing.setCompanyName(updated.getCompanyName());
        if (updated.getRole()        != null) existing.setRole(updated.getRole());
        if (updated.getSkills()      != null) existing.setSkills(updated.getSkills());
        if (updated.getWalkInDate()  != null) existing.setWalkInDate(updated.getWalkInDate());
        if (updated.getWalkInTime()  != null) existing.setWalkInTime(updated.getWalkInTime());
        if (updated.getCity()        != null) existing.setCity(updated.getCity());
        if (updated.getLocation()    != null) existing.setLocation(updated.getLocation());
        if (updated.getContactInfo() != null) existing.setContactInfo(updated.getContactInfo());
        if (updated.getDescription() != null) existing.setDescription(updated.getDescription());
        if (updated.getStatus()      != null) existing.setStatus(updated.getStatus());
        return walkInRepository.save(existing);
    }

    public void deleteWalkIn(String id, String userEmail, boolean isAdmin) {
        WalkIn walkIn = walkInRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Walk-in not found"));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!isAdmin && !walkIn.getPostedById().equals(user.getId())) {
            throw new RuntimeException("Not authorized to delete this walk-in");
        }
        walkInRepository.deleteById(id);
    }

    public Optional<WalkIn> getById(String id) {
        return walkInRepository.findById(id);
    }

    public List<WalkIn> getAll() {
        return walkInRepository.findAllByOrderByCreatedAtDesc();
    }

    private void expirePastWalkIns() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        List<WalkIn> active = walkInRepository.findByStatusOrderByWalkInDateAsc("ACTIVE");
        List<WalkIn> toExpire = new ArrayList<>();
        for (WalkIn w : active) {
            try {
                if (LocalDate.parse(w.getWalkInDate()).isBefore(today)) {
                    w.setStatus("EXPIRED");
                    toExpire.add(w);
                }
            } catch (java.time.format.DateTimeParseException e) {
                // Bad date string — expire it so it stops being served, and log for cleanup.
                log.warn("WalkIn {} has unparseable date '{}' — marking EXPIRED", w.getId(), w.getWalkInDate());
                w.setStatus("EXPIRED");
                toExpire.add(w);
            }
        }
        if (!toExpire.isEmpty()) {
            walkInRepository.saveAll(toExpire);
        }
    }
}
