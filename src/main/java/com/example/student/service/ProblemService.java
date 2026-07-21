package com.example.student.service;

import com.example.student.dto.ProblemDetailDTO;
import com.example.student.dto.ProblemSummaryDTO;
import com.example.student.model.ProblemQuestion;
import com.example.student.repository.ProblemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProblemService {

    private final ProblemRepository repo;
    private final CacheService cacheService;

    public ProblemService(ProblemRepository repo, CacheService cacheService) {
        this.repo = repo;
        this.cacheService = cacheService;
    }

    public List<ProblemSummaryDTO> list(String track) {
        String cacheKey = track != null ? "summary:track:" + track : "summary:all";
        return cacheService.get("problems", cacheKey, () -> {
            List<ProblemQuestion> rows = track != null
                    ? repo.findByTrackOrderByOrderIndexAsc(track)
                    : repo.findAllByOrderByOrderIndexAsc();
            return rows.stream().map(ProblemSummaryDTO::from).toList();
        });
    }

    public ProblemDetailDTO getDetail(String id) {
        return cacheService.get("problems", "detail:" + id, () -> {
            ProblemQuestion p = repo.findById(id).orElse(null);
            return p != null ? ProblemDetailDTO.from(p) : null;
        });
    }

    /** Full entity for the judge — never returned to clients. */
    public ProblemQuestion getEntity(String id) {
        return repo.findById(id).orElse(null);
    }
}
