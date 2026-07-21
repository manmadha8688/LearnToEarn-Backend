package com.example.student.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 1 code runner for POST /api/code/execute: write user source to a temp
 * dir, compile if needed, run under a hard 5s wall clock, capture ≤10KB of
 * output, then delete everything. Guards applied on every run:
 *   - at most {@value #MAX_CONCURRENT} concurrent executions (rest queue on the semaphore)
 *   - 5s timeout → process is force-killed, status TIMEOUT
 *   - output capped at {@value #MAX_OUTPUT} bytes per stream
 *   - Java runs with -Xmx256m
 *   - dangerous constructs are blocked before anything runs (status BLOCKED)
 *
 * Not a real sandbox — the blocklist + timeout are Phase 1 mitigations only.
 */
@Service
public class CodeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(CodeExecutionService.class);

    static final List<String> SUPPORTED = List.of("python", "java", "c", "cpp");

    private static final int MAX_CONCURRENT = 10;
    private static final int MAX_OUTPUT = 10 * 1024;          // 10KB per stream
    private static final long RUN_TIMEOUT_SEC = 5;
    private static final long COMPILE_TIMEOUT_SEC = 10;

    // Case-sensitive substrings blocked per language. The bare Python "import"
    // rule from the spec is applied as __import__ (the dynamic-import bypass) so
    // ordinary safe imports like `import math` still work. Thread-creation calls
    // are blocked in C/C++ so a program can't spawn threads to dodge the single
    // -process ulimit caps.
    private static final Map<String, List<String>> BLOCKLIST = Map.of(
        "python", List.of("import os", "import socket", "import subprocess", "__import__"),
        "java",   List.of("Runtime.exec", "ProcessBuilder", "System.exit"),
        "c",      List.of("system(", "fork(", "exec(", "popen(", "pthread_create", "CreateThread"),
        "cpp",    List.of("system(", "fork(", "exec(", "popen(", "pthread_create", "CreateThread")
    );

    // Blocked tokens that specifically create threads — surfaced with a clearer message.
    private static final List<String> THREAD_TOKENS = List.of("pthread_create", "CreateThread");

    private static final Pattern JAVA_PUBLIC_CLASS =
        Pattern.compile("public\\s+(?:final\\s+|abstract\\s+)?class\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    // Executable names differ across environments: alpine/Linux (Docker prod) vs
    // a developer's Windows/macOS box. Try each candidate and use the first that
    // actually launches, so the same code runs everywhere without config.
    private static final boolean IS_WINDOWS =
        System.getProperty("os.name", "").toLowerCase().contains("win");
    // On Windows `python` is the real interpreter and `python3` is usually a
    // Store alias stub (launches, prints an error, exits) — so prefer `python`
    // there. On Linux/Docker `python3` is canonical, so prefer it.
    private static final List<String> PYTHON_CMDS = IS_WINDOWS
        ? List.of("python", "python3")
        : List.of("python3", "python");
    private static final List<String> C_CMDS      = List.of("gcc", "cc", "clang");
    private static final List<String> CPP_CMDS    = List.of("g++", "c++", "clang++");
    private static final String NATIVE_BIN = IS_WINDOWS ? "program.exe" : "program";

    /** Thrown internally when none of a language's toolchain candidates are installed. */
    private static final class ToolUnavailableException extends Exception { }

    // Caps total concurrent executions; extra callers block here until a slot frees.
    private final Semaphore slots = new Semaphore(MAX_CONCURRENT, true);

    public boolean isSupported(String language) {
        return language != null && SUPPORTED.contains(language);
    }

    /** Runs the code and returns { output, error, status, executionTime }. Never throws. */
    public Map<String, Object> execute(String code, String language) {
        String blocked = blockedReason(code, language);
        if (blocked != null) return result(null, blocked, "BLOCKED", 0);

        try {
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return result(null, "Execution was cancelled.", "ERROR", 0);
        }

        Path dir = null;
        try {
            dir = Files.createTempDirectory("codeexec-");
            return switch (language) {
                case "python" -> runPython(dir, code);
                case "java"   -> runJava(dir, code);
                case "c"      -> runNative(dir, code, "c");
                case "cpp"    -> runNative(dir, code, "cpp");
                default       -> result(null, "Unsupported language.", "ERROR", 0);
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return result(null, "Execution was cancelled.", "ERROR", 0);
        } catch (Exception e) {
            log.warn("Code execution failed ({}): {}", language, e.getMessage());
            return result(null, "Could not run your code. Please try again.", "ERROR", 0);
        } finally {
            slots.release();
            deleteQuietly(dir);
        }
    }

    // ── Judging (stdin → stdout against test cases) ────────────────────────

    /** One test case handed to the judge (decoupled from the persistence model). */
    public record Case(String input, String expectedOutput, boolean sample) {}

    private interface Runner {
        Proc run(Path dir, String stdin) throws IOException, InterruptedException, ToolUnavailableException;
    }
    private record Prepared(String error, Runner runner) {}

    /**
     * Compiles once (if needed), then runs the code against each case, comparing
     * trimmed stdout to expected. `onlySamples` = Run (visible cases only);
     * otherwise Submit (all cases, stops at the first failure). Never throws.
     * Returns { status, message, passed, total, results[], executionTime }.
     */
    public Map<String, Object> judge(String code, String language, List<Case> cases, boolean onlySamples) {
        String blocked = blockedReason(code, language);
        if (blocked != null) return verdict("BLOCKED", blocked, 0, 0, List.of(), 0);

        List<Case> toRun = new ArrayList<>();
        for (Case c : cases) if (!onlySamples || c.sample()) toRun.add(c);
        if (toRun.isEmpty()) return verdict("ERROR", "No test cases are available.", 0, 0, List.of(), 0);

        try {
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return verdict("ERROR", "Execution was cancelled.", 0, toRun.size(), List.of(), 0);
        }

        Path dir = null;
        try {
            dir = Files.createTempDirectory("codejudge-");
            Prepared prep = prepare(dir, code, language);
            if (prep.error() != null) return verdict("COMPILE_ERROR", prep.error(), 0, toRun.size(), List.of(), 0);

            List<Map<String, Object>> results = new ArrayList<>();
            int passed = 0;
            long totalMs = 0;
            String overall = "ACCEPTED";
            for (int i = 0; i < toRun.size(); i++) {
                Case c = toRun.get(i);
                long start = System.currentTimeMillis();
                Proc p;
                try {
                    p = prep.runner().run(dir, c.input() == null ? "" : c.input());
                } catch (ToolUnavailableException e) {
                    return verdict("COMPILE_ERROR", languageLabel(language) + " is not available on this server.",
                            passed, toRun.size(), results, totalMs);
                }
                long ms = System.currentTimeMillis() - start;
                totalMs += ms;
                Map<String, Object> r = evalCase(i, c, p, ms);
                results.add(r);
                if (Boolean.TRUE.equals(r.get("passed"))) {
                    passed++;
                } else {
                    if ("ACCEPTED".equals(overall)) overall = (String) r.get("status");
                    if (!onlySamples) break; // Submit: fail fast at the first failing case
                }
            }
            return verdict(overall, null, passed, toRun.size(), results, totalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return verdict("ERROR", "Execution was cancelled.", 0, toRun.size(), List.of(), 0);
        } catch (Exception e) {
            log.warn("Judge failed ({}): {}", language, e.getMessage());
            return verdict("ERROR", "Could not run your code. Please try again.", 0, toRun.size(), List.of(), 0);
        } finally {
            slots.release();
            deleteQuietly(dir);
        }
    }

    /** Writes source + compiles once (java/c/cpp); returns a reusable per-case runner or an error. */
    private Prepared prepare(Path dir, String code, String language) throws IOException, InterruptedException {
        switch (language) {
            case "python" -> {
                Files.writeString(dir.resolve("main.py"), code, StandardCharsets.UTF_8);
                return new Prepared(null, (d, stdin) ->
                    spawnAny(d, PYTHON_CMDS, List.of("main.py"), RUN_TIMEOUT_SEC, stdin));
            }
            case "java" -> {
                Matcher m = JAVA_PUBLIC_CLASS.matcher(code);
                String cls = m.find() ? m.group(1) : "Main";
                Files.writeString(dir.resolve(cls + ".java"), code, StandardCharsets.UTF_8);
                Proc comp;
                try {
                    comp = spawnAny(dir, List.of("javac"), List.of(cls + ".java"), COMPILE_TIMEOUT_SEC);
                } catch (ToolUnavailableException e) {
                    return new Prepared("The Java compiler is not available on this server.", null);
                }
                if (comp.timedOut) return new Prepared("Compilation timed out.", null);
                if (comp.exit != 0) return new Prepared(firstNonEmpty(comp.err, comp.out, "Compilation failed."), null);
                return new Prepared(null, (d, stdin) ->
                    spawn(d, List.of("java", "-Xmx256m", "-cp", ".", cls), RUN_TIMEOUT_SEC, stdin));
            }
            case "c", "cpp" -> {
                boolean cpp = language.equals("cpp");
                String src = cpp ? "main.cpp" : "main.c";
                Files.writeString(dir.resolve(src), code, StandardCharsets.UTF_8);
                Proc comp;
                try {
                    comp = spawnAny(dir, cpp ? CPP_CMDS : C_CMDS, List.of(src, "-o", NATIVE_BIN), COMPILE_TIMEOUT_SEC);
                } catch (ToolUnavailableException e) {
                    return new Prepared("The " + (cpp ? "C++" : "C") + " compiler is not available on this server.", null);
                }
                if (comp.timedOut) return new Prepared("Compilation timed out.", null);
                if (comp.exit != 0) return new Prepared(firstNonEmpty(comp.err, comp.out, "Compilation failed."), null);
                return new Prepared(null, (d, stdin) -> spawn(d, nativeRunCommand(d), RUN_TIMEOUT_SEC, stdin));
            }
            default -> { return new Prepared("Unsupported language.", null); }
        }
    }

    /** Runs one case's outcome into a result map (full detail for sample cases only). */
    private Map<String, Object> evalCase(int index, Case c, Proc p, long ms) {
        String status;
        boolean passed = false;
        if (p.timedOut) {
            status = "TLE";
        } else if (isMemoryKill(p.exit)) {
            status = "MLE";
        } else if (p.exit != 0) {
            status = "RUNTIME_ERROR";
        } else {
            passed = normalize(p.out).equals(normalize(c.expectedOutput()));
            status = passed ? "PASSED" : "WRONG_ANSWER";
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("index", index);
        r.put("sample", c.sample());
        r.put("passed", passed);
        r.put("status", status);
        r.put("executionTime", ms);
        if (c.sample()) {
            // Visible cases: reveal everything so the student can debug.
            r.put("input", c.input());
            r.put("expected", c.expectedOutput());
            r.put("actual", p.timedOut ? "" : p.out);
            if (p.exit != 0 && !p.timedOut) r.put("stderr", truncate(p.err));
        }
        return r;
    }

    /** Compares ignoring trailing whitespace per line and trailing blank lines. */
    private static String normalize(String s) {
        if (s == null) return "";
        String[] lines = s.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            int end = line.length();
            while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) end--;
            sb.append(line, 0, end).append('\n');
        }
        String out = sb.toString();
        int e = out.length();
        while (e > 0 && out.charAt(e - 1) == '\n') e--;
        return out.substring(0, e);
    }

    private Map<String, Object> verdict(String status, String message, int passed, int total,
                                        List<Map<String, Object>> results, long ms) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("message", message);
        m.put("passed", passed);
        m.put("total", total);
        m.put("results", results);
        m.put("executionTime", ms);
        return m;
    }

    private static String languageLabel(String lang) {
        return switch (lang) {
            case "python" -> "Python";
            case "java" -> "Java";
            case "c" -> "C";
            case "cpp" -> "C++";
            default -> lang;
        };
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 2048 ? s.substring(0, 2048) + "…" : s;
    }

    // ── Per-language runners (single Run, no test cases) ───────────────────

    private Map<String, Object> runPython(Path dir, String code) throws IOException, InterruptedException {
        Files.writeString(dir.resolve("main.py"), code, StandardCharsets.UTF_8);
        long start = System.currentTimeMillis();
        Proc p;
        try {
            p = spawnAny(dir, PYTHON_CMDS, List.of("main.py"), RUN_TIMEOUT_SEC);
        } catch (ToolUnavailableException e) {
            return result(null, "Python is not available on this server.", "ERROR", 0);
        }
        return mapRun(p, System.currentTimeMillis() - start);
    }

    private Map<String, Object> runJava(Path dir, String code) throws IOException, InterruptedException {
        Matcher m = JAVA_PUBLIC_CLASS.matcher(code);
        String className = m.find() ? m.group(1) : "Main";
        Files.writeString(dir.resolve(className + ".java"), code, StandardCharsets.UTF_8);

        Proc compile;
        try {
            compile = spawnAny(dir, List.of("javac"), List.of(className + ".java"), COMPILE_TIMEOUT_SEC);
        } catch (ToolUnavailableException e) {
            return result(null, "The Java compiler is not available on this server.", "ERROR", 0);
        }
        if (compile.timedOut) return result(null, "Compilation timed out.", "ERROR", 0);
        if (compile.exit != 0) return result(null, firstNonEmpty(compile.err, compile.out, "Compilation failed."), "ERROR", 0);

        long start = System.currentTimeMillis();
        Proc p = spawn(dir, List.of("java", "-Xmx256m", "-cp", ".", className), RUN_TIMEOUT_SEC);
        return mapRun(p, System.currentTimeMillis() - start);
    }

    private Map<String, Object> runNative(Path dir, String code, String lang) throws IOException, InterruptedException {
        boolean cpp = lang.equals("cpp");
        String src = cpp ? "main.cpp" : "main.c";
        Files.writeString(dir.resolve(src), code, StandardCharsets.UTF_8);

        Proc compile;
        try {
            compile = spawnAny(dir, cpp ? CPP_CMDS : C_CMDS, List.of(src, "-o", NATIVE_BIN), COMPILE_TIMEOUT_SEC);
        } catch (ToolUnavailableException e) {
            return result(null, "The " + (cpp ? "C++" : "C") + " compiler is not available on this server.", "ERROR", 0);
        }
        if (compile.timedOut) return result(null, "Compilation timed out.", "ERROR", 0);
        if (compile.exit != 0) return result(null, firstNonEmpty(compile.err, compile.out, "Compilation failed."), "ERROR", 0);

        long start = System.currentTimeMillis();
        Proc p = spawn(dir, nativeRunCommand(dir), RUN_TIMEOUT_SEC);
        long ms = System.currentTimeMillis() - start;
        if (!p.timedOut && isMemoryKill(p.exit)) return result(p.out, "Memory Limit Exceeded", "MLE", ms);
        return mapRun(p, ms);
    }

    /**
     * Native (C/C++) run command. On POSIX (Docker/Linux prod) wrap the compiled
     * binary in a shell that caps virtual memory to 256MB and CPU time to 5s via
     * ulimit, so a runaway allocation or tight loop is killed by the OS rather than
     * starving the instance. On Windows dev there is no ulimit, so run it directly.
     */
    private static List<String> nativeRunCommand(Path dir) {
        if (IS_WINDOWS) return List.of(dir.resolve(NATIVE_BIN).toString());
        return List.of("sh", "-c", "ulimit -v 262144; ulimit -t 5; exec ./" + NATIVE_BIN);
    }

    /**
     * True when a non-timed-out process exited via a ulimit kill: SIGKILL from the
     * virtual-memory cap → 137 (128+9), or SIGXCPU from the CPU-time cap → 152 (128+24).
     * Surfaced as a Memory Limit Exceeded verdict.
     */
    private static boolean isMemoryKill(int exit) {
        return exit == 137 || exit == 152;
    }

    /** Maps a finished/killed process to the { output, error, status, executionTime } shape. */
    private Map<String, Object> mapRun(Proc p, long ms) {
        if (p.timedOut) return result(p.out, "Time Limit Exceeded", "TIMEOUT", ms);
        if (p.exit != 0) return result(p.out, firstNonEmpty(p.err, "Program exited with code " + p.exit), "ERROR", ms);
        return result(p.out, isBlank(p.err) ? null : p.err, "SUCCESS", ms);
    }

    private Proc spawnAny(Path dir, List<String> candidates, List<String> tailArgs, long timeoutSec)
            throws InterruptedException, ToolUnavailableException {
        return spawnAny(dir, candidates, tailArgs, timeoutSec, null);
    }

    /** Runs [candidate, tailArgs...] using the first candidate that launches. */
    private Proc spawnAny(Path dir, List<String> candidates, List<String> tailArgs, long timeoutSec, String stdin)
            throws InterruptedException, ToolUnavailableException {
        for (String exe : candidates) {
            List<String> cmd = new ArrayList<>(candidates.size());
            cmd.add(exe);
            cmd.addAll(tailArgs);
            try {
                return spawn(dir, cmd, timeoutSec, stdin);
            } catch (IOException notFound) {
                // this candidate isn't installed / not on PATH — try the next one
            }
        }
        throw new ToolUnavailableException();
    }

    // ── Process plumbing ──────────────────────────────────────────────────

    private record Proc(int exit, String out, String err, boolean timedOut) {}

    private Proc spawn(Path dir, List<String> command, long timeoutSec) throws IOException, InterruptedException {
        return spawn(dir, command, timeoutSec, null);
    }

    private Proc spawn(Path dir, List<String> command, long timeoutSec, String stdin) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command).directory(dir.toFile());
        Process process = pb.start();
        // Feed stdin then close it (EOF) so programs waiting on input() proceed
        // instead of hanging until the timeout.
        try (var os = process.getOutputStream()) {
            if (stdin != null && !stdin.isEmpty()) {
                os.write(stdin.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        } catch (IOException ignored) { /* program may not read stdin / closed early */ }
        // Drain both streams on their own threads so a chatty program can't
        // deadlock by filling a pipe buffer while we wait.
        Gobbler out = new Gobbler(process.getInputStream());
        Gobbler err = new Gobbler(process.getErrorStream());
        Thread tOut = new Thread(out); Thread tErr = new Thread(err);
        tOut.start(); tErr.start();

        boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
            tOut.join(500); tErr.join(500);
            return new Proc(-1, out.text(), err.text(), true);
        }
        tOut.join(1000); tErr.join(1000);
        return new Proc(process.exitValue(), out.text(), err.text(), false);
    }

    /** Reads a stream, keeping at most {@link #MAX_OUTPUT} bytes but draining the rest. */
    private static final class Gobbler implements Runnable {
        private final InputStream in;
        private final StringBuilder sb = new StringBuilder();
        Gobbler(InputStream in) { this.in = in; }

        @Override public void run() {
            byte[] buf = new byte[4096];
            int total = 0, n;
            try {
                while ((n = in.read(buf)) != -1) {
                    if (total < MAX_OUTPUT) {
                        int take = Math.min(n, MAX_OUTPUT - total);
                        sb.append(new String(buf, 0, take, StandardCharsets.UTF_8));
                        total += take;
                    }
                    // keep reading past the cap so the process never blocks on a full pipe
                }
            } catch (IOException ignored) {
                // stream closed (e.g. process force-killed) — stop reading
            }
        }
        String text() { return sb.toString(); }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    String blockedReason(String code, String language) {
        List<String> banned = BLOCKLIST.getOrDefault(language, List.of());
        for (String token : banned) {
            if (code.contains(token)) {
                if (THREAD_TOKENS.contains(token)) return "Thread creation is not allowed.";
                return "Blocked for safety: \"" + token + "\" is not allowed in this runner.";
            }
        }
        return null;
    }

    private Map<String, Object> result(String output, String error, String status, long ms) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("output", output);
        m.put("error", error);
        m.put("status", status);
        m.put("executionTime", ms);
        return m;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String firstNonEmpty(String... vals) {
        for (String v : vals) if (!isBlank(v)) return v;
        return "";
    }

    private void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        } catch (IOException e) {
            log.warn("Temp cleanup failed for {}: {}", dir, e.getMessage());
        }
    }

    // ── Orphaned temp-dir sweeping ─────────────────────────────────────────
    // Every run cleans up its own dir in a finally block, but a hard crash / OOM
    // kill / container restart can leave a "codeexec-"/"codejudge-" dir behind.
    // Sweep those (older than 10 min so an in-flight run is never touched) once on
    // startup and again every 30 minutes so temp storage can't leak over time.

    private static final long ORPHAN_AGE_MS = 10 * 60 * 1000L;
    private static final String[] TEMP_PREFIXES = { "codeexec-", "codejudge-" };

    @PostConstruct
    public void sweepOrphansOnStartup() {
        sweepOrphans("startup");
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000L)
    public void sweepOrphansScheduled() {
        sweepOrphans("scheduled");
    }

    private void sweepOrphans(String phase) {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir", "."));
        long cutoff = System.currentTimeMillis() - ORPHAN_AGE_MS;
        int removed = 0;
        try (var list = Files.list(tmp)) {
            for (Path p : (Iterable<Path>) list::iterator) {
                if (!Files.isDirectory(p)) continue;
                String name = p.getFileName().toString();
                boolean ours = false;
                for (String prefix : TEMP_PREFIXES) if (name.startsWith(prefix)) { ours = true; break; }
                if (!ours) continue;
                try {
                    if (Files.getLastModifiedTime(p).toMillis() < cutoff) {
                        deleteQuietly(p);
                        removed++;
                    }
                } catch (IOException ignored) { /* vanished between listing and stat — fine */ }
            }
        } catch (IOException e) {
            log.warn("Temp orphan sweep ({}) could not read {}: {}", phase, tmp, e.getMessage());
            return;
        }
        if (removed > 0) {
            log.warn("Cleaned {} orphaned code-execution temp dir(s) during {} sweep.", removed, phase);
        }
    }
}
