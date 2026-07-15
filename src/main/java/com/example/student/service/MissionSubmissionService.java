package com.example.student.service;

import com.example.student.model.MissionSubmission;
import com.example.student.repository.MissionRepository;
import com.example.student.repository.MissionSubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.regex.Pattern;

@Service
public class MissionSubmissionService {

    private static final int MAX_URL = 500;
    // http(s) URL with a host; anything else is rejected
    private static final Pattern URL = Pattern.compile("^https?://[^\\s/$.?#].[^\\s]*$", Pattern.CASE_INSENSITIVE);

    private final MissionSubmissionRepository submissionRepo;
    private final MissionRepository missionRepo;

    public MissionSubmissionService(MissionSubmissionRepository submissionRepo, MissionRepository missionRepo) {
        this.submissionRepo = submissionRepo;
        this.missionRepo = missionRepo;
    }

    /** Current hunter's submission for a mission, or null if none yet. */
    public MissionSubmission get(String userId, String missionId) {
        return submissionRepo.findByUserIdAndMissionId(userId, missionId).orElse(null);
    }

    /** Create or update the hunter's repo + deploy links for a mission. */
    public MissionSubmission save(String userId, String missionId, String repoUrl, String deployUrl) {
        if (!missionRepo.existsById(missionId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mission not found");

        String repo   = clean(repoUrl,   "Repository link");
        String deploy = clean(deployUrl, "Live demo link");

        MissionSubmission s = submissionRepo.findByUserIdAndMissionId(userId, missionId)
                .orElseGet(() -> MissionSubmission.builder()
                        .userId(userId)
                        .missionId(missionId)
                        .build());
        s.setRepoUrl(repo);
        s.setDeployUrl(deploy);
        return submissionRepo.save(s);
    }

    /** Trim, validate as an http(s) URL, and enforce a length cap. Blank → null (clears the field). */
    private String clean(String raw, String label) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;
        if (v.length() > MAX_URL)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is too long");
        if (!URL.matcher(v).matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must be a valid http(s) URL");
        return v;
    }
}
