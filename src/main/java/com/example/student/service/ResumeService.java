package com.example.student.service;

import com.example.student.model.Resume;
import com.example.student.repository.ResumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

@Service
public class ResumeService {

    // Guard against oversized payloads — a resume is small; anything huge is abuse.
    private static final int MAX_KEYS = 40;

    private final ResumeRepository resumeRepo;

    public ResumeService(ResumeRepository resumeRepo) {
        this.resumeRepo = resumeRepo;
    }

    /** The user's saved resume content, or an empty map if they have none yet. */
    public Map<String, Object> get(String userId) {
        return resumeRepo.findByUserId(userId)
                .map(Resume::getData)
                .orElse(Collections.emptyMap());
    }

    /** Create or update the user's resume content. */
    public Map<String, Object> save(String userId, Map<String, Object> data) {
        if (data == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume content is required");
        if (data.size() > MAX_KEYS)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume content is too large");

        Resume resume = resumeRepo.findByUserId(userId)
                .orElseGet(() -> Resume.builder().userId(userId).build());
        resume.setData(data);
        return resumeRepo.save(resume).getData();
    }
}
