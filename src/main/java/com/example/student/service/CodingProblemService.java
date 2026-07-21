package com.example.student.service;

import com.example.student.dto.CodeSubmissionDTO;
import com.example.student.dto.CodingProblemDTO;
import com.example.student.dto.CodingProblemSummaryDTO;
import com.example.student.model.CodeSubmission;
import com.example.student.model.CodingProblem;
import com.example.student.repository.CodeSubmissionRepository;
import com.example.student.repository.CodingProblemRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coding-platform read + judge orchestration. The actual compile/run is delegated
 * unchanged to {@link CodeExecutionService}; this service only assembles test cases,
 * shapes verdicts, and persists submissions.
 */
@Service
public class CodingProblemService {

    private static final String CACHE = "codingProblems";

    private final CodingProblemRepository problemRepo;
    private final CodeSubmissionRepository submissionRepo;
    private final CodeExecutionService codeService;
    private final CacheService cacheService;

    public CodingProblemService(CodingProblemRepository problemRepo,
                                CodeSubmissionRepository submissionRepo,
                                CodeExecutionService codeService,
                                CacheService cacheService) {
        this.problemRepo = problemRepo;
        this.submissionRepo = submissionRepo;
        this.codeService = codeService;
        this.cacheService = cacheService;
    }

    // ── Reads (public, cached) ─────────────────────────────────────────────

    public List<CodingProblemSummaryDTO> listProblems() {
        return cacheService.get(CACHE, "all", () ->
                problemRepo.findAllByOrderByOrderIndexAsc().stream()
                        .map(CodingProblemSummaryDTO::from)
                        .toList());
    }

    /** Public problem view (hidden test cases stripped). Null if not found. */
    public CodingProblemDTO getProblemDTO(String id) {
        return cacheService.get(CACHE, "id:" + id, () -> {
            CodingProblem p = problemRepo.findById(id).orElse(null);
            return p == null ? null : CodingProblemDTO.from(p);
        });
    }

    // ── Judge (authenticated) ──────────────────────────────────────────────

    /** Run against the visible sample cases only; full per-case detail returned. */
    public Map<String, Object> run(CodingProblem problem, String code, String language) {
        List<CodeExecutionService.Case> cases = toCases(problem.getSampleTestCases(), true);
        if (cases.isEmpty()) {
            return Map.of("status", "ERROR", "message", "This problem has no sample test cases yet.",
                    "passed", 0, "total", 0, "results", List.of(), "executionTime", 0);
        }
        // onlySamples=true → run every sample without fail-fast, revealing detail.
        return codeService.judge(code, language, cases, true);
    }

    /**
     * Submit against all hidden cases (fail-fast), persist the result, and return a
     * client-shaped verdict. Hidden inputs/outputs are never included in the response.
     */
    public Map<String, Object> submit(String userId, CodingProblem problem, String code, String language) {
        List<CodeExecutionService.Case> cases = toCases(problem.getHiddenTestCases(), false);
        if (cases.isEmpty()) {
            return Map.of("status", "ERROR", "verdict", "Error",
                    "message", "This problem has no hidden test cases yet.",
                    "passed", 0, "total", 0, "runtime", 0, "failedCase", 0);
        }

        Map<String, Object> judged = codeService.judge(code, language, cases, false);
        String status = (String) judged.get("status");
        int passed = asInt(judged.get("passed"));
        int total = asInt(judged.get("total"));
        long runtime = asLong(judged.get("executionTime"));
        String verdict = verdictLabel(status);
        int failedCase = firstFailingCase(judged.get("results"));

        // Persist only real judge runs (not blocked/compile-config errors with 0 total).
        if (!"BLOCKED".equals(status)) {
            CodeSubmission sub = new CodeSubmission();
            sub.setUserId(userId);
            sub.setProblemId(problem.getId());
            sub.setLanguage(language);
            sub.setCode(code);
            sub.setVerdict(verdict);
            sub.setPassedCases(passed);
            sub.setTotalCases(total);
            sub.setRuntime(runtime);
            sub.setSubmittedAt(Instant.now());
            submissionRepo.save(sub);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", status);
        out.put("verdict", verdict);
        out.put("passed", passed);
        out.put("total", total);
        out.put("runtime", runtime);
        out.put("failedCase", failedCase);
        out.put("message", judged.get("message"));
        return out;
    }

    public List<CodeSubmissionDTO> submissions(String userId, String problemId) {
        return submissionRepo.findByUserIdAndProblemIdOrderBySubmittedAtDesc(userId, problemId).stream()
                .map(CodeSubmissionDTO::from)
                .toList();
    }

    public CodingProblem findEntity(String id) {
        return problemRepo.findById(id).orElse(null);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static List<CodeExecutionService.Case> toCases(List<CodingProblem.IOCase> src, boolean sample) {
        List<CodeExecutionService.Case> out = new ArrayList<>();
        if (src == null) return out;
        for (CodingProblem.IOCase c : src) {
            out.add(new CodeExecutionService.Case(c.getInput(), c.getExpectedOutput(), sample));
        }
        return out;
    }

    /** 1-based index of the first failing case, or 0 if none failed / no results. */
    @SuppressWarnings("unchecked")
    private static int firstFailingCase(Object results) {
        if (!(results instanceof List<?> list)) return 0;
        for (Object o : list) {
            if (o instanceof Map<?, ?> m && Boolean.FALSE.equals(m.get("passed"))) {
                Object idx = ((Map<String, Object>) m).get("index");
                return (idx instanceof Number n ? n.intValue() : 0) + 1;
            }
        }
        return 0;
    }

    private static String verdictLabel(String status) {
        return switch (status == null ? "" : status) {
            case "ACCEPTED"      -> "Accepted";
            case "WRONG_ANSWER"  -> "Wrong Answer";
            case "TLE"           -> "Time Limit Exceeded";
            case "RUNTIME_ERROR" -> "Runtime Error";
            case "COMPILE_ERROR" -> "Compile Error";
            case "BLOCKED"       -> "Blocked";
            default              -> "Error";
        };
    }

    private static int asInt(Object o) { return o instanceof Number n ? n.intValue() : 0; }
    private static long asLong(Object o) { return o instanceof Number n ? n.longValue() : 0L; }
}
