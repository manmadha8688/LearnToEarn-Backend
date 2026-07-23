package com.example.student.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Proactive load shedding: when the JVM is critically low on memory, refuse new work with
 * HTTP 503 instead of risking an OutOfMemoryError that would crash the whole instance.
 * Runs right after {@link ApiCacheControlFilter} (HIGHEST_PRECEDENCE) so the check happens
 * before any request processing / DB work.
 *
 * <p><b>Correct memory metric.</b> We shed on <em>actually available</em> heap
 * ({@code maxMemory - used}), NOT {@code Runtime.freeMemory()}. {@code freeMemory()} reports
 * free space inside the currently-committed heap, which is often only a few MB even when the
 * JVM can still grow the heap by hundreds of MB — using it caused constant false-positive
 * 503s that surfaced in the browser as CORS errors (see below).
 *
 * <p><b>Never masks as CORS.</b> This filter runs before Spring Security's CORS filter, so a
 * 503 written here would otherwise lack {@code Access-Control-Allow-Origin} and appear to the
 * browser as a "CORS error / server not responding". To avoid that we (1) never shed CORS
 * preflight ({@code OPTIONS}) requests and (2) mirror the allow-listed {@code Origin} onto the
 * 503 so a genuine overload surfaces as a real 503 the frontend can handle.
 *
 * <p>A small set of paths stay reachable even under pressure: the health probe, public read
 * endpoints, and auth refresh (so a client can recover its session).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class LoadSheddingFilter extends OncePerRequestFilter {

    // Shed only when available heap (max - used) drops below this fraction of the max heap,
    // with an absolute floor so tiny heaps don't trip constantly. Tuned to fire only in a
    // genuine near-OOM situation, never in normal steady-state operation.
    private static final double MIN_FREE_FRACTION = 0.06;             // < 6% of max heap free
    private static final long   MIN_FREE_FLOOR    = 16L * 1024 * 1024; // and never below 16MB

    private final Set<String> allowedOrigins;

    public LoadSheddingFilter(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174}") String allowedOriginsStr) {
        this.allowedOrigins = Arrays.stream(allowedOriginsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // Never shed CORS preflight — an OPTIONS 503 carries no CORS headers and blocks
        // every subsequent call (including login) with a misleading browser CORS error.
        if (!"OPTIONS".equalsIgnoreCase(request.getMethod())
                && !isExempt(request)
                && isMemoryCritical()) {
            addCorsHeaders(request, response);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.setHeader("Retry-After", "5");
            response.getWriter().write(
                "{\"error\":\"Server is under high load. Please try again in a moment.\",\"retryAfter\":5}");
            return;
        }

        chain.doFilter(request, response);
    }

    /** True only when the JVM is genuinely near its heap ceiling. */
    private boolean isMemoryCritical() {
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        if (max == Long.MAX_VALUE) return false; // no heap cap → can't reason about pressure
        long used = rt.totalMemory() - rt.freeMemory();
        long available = max - used;
        long threshold = Math.max(MIN_FREE_FLOOR, (long) (max * MIN_FREE_FRACTION));
        return available < threshold;
    }

    /** Mirror the request Origin (when allow-listed) so a genuine 503 is a valid CORS response. */
    private void addCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin != null && allowedOrigins.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Vary", "Origin");
        }
    }

    /** Paths that must stay reachable even when shedding load. */
    private boolean isExempt(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) return false;
        String method = request.getMethod();
        if ("GET".equals(method) && "/api/health".equals(uri)) return true;
        if ("GET".equals(method) && uri.startsWith("/api/public/")) return true;
        if ("POST".equals(method) && "/api/auth/refresh".equals(uri)) return true;
        return false;
    }
}
