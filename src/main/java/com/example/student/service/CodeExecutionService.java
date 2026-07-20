package com.example.student.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    // ordinary safe imports like `import math` still work.
    private static final Map<String, List<String>> BLOCKLIST = Map.of(
        "python", List.of("import os", "import socket", "import subprocess", "__import__"),
        "java",   List.of("Runtime.exec", "ProcessBuilder", "System.exit"),
        "c",      List.of("system(", "fork(", "exec(", "popen("),
        "cpp",    List.of("system(", "fork(", "exec(", "popen(")
    );

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

    // ── Per-language runners ──────────────────────────────────────────────

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
        Proc p = spawn(dir, List.of(dir.resolve(NATIVE_BIN).toString()), RUN_TIMEOUT_SEC);
        return mapRun(p, System.currentTimeMillis() - start);
    }

    /** Maps a finished/killed process to the { output, error, status, executionTime } shape. */
    private Map<String, Object> mapRun(Proc p, long ms) {
        if (p.timedOut) return result(p.out, "Time Limit Exceeded", "TIMEOUT", ms);
        if (p.exit != 0) return result(p.out, firstNonEmpty(p.err, "Program exited with code " + p.exit), "ERROR", ms);
        return result(p.out, isBlank(p.err) ? null : p.err, "SUCCESS", ms);
    }

    /** Runs [candidate, tailArgs...] using the first candidate that launches. */
    private Proc spawnAny(Path dir, List<String> candidates, List<String> tailArgs, long timeoutSec)
            throws InterruptedException, ToolUnavailableException {
        for (String exe : candidates) {
            List<String> cmd = new ArrayList<>(candidates.size());
            cmd.add(exe);
            cmd.addAll(tailArgs);
            try {
                return spawn(dir, cmd, timeoutSec);
            } catch (IOException notFound) {
                // this candidate isn't installed / not on PATH — try the next one
            }
        }
        throw new ToolUnavailableException();
    }

    // ── Process plumbing ──────────────────────────────────────────────────

    private record Proc(int exit, String out, String err, boolean timedOut) {}

    private Proc spawn(Path dir, List<String> command, long timeoutSec) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command).directory(dir.toFile());
        Process process = pb.start();
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
}
