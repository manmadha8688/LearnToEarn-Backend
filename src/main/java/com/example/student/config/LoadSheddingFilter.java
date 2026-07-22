package com.example.student.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Proactive load shedding: when the JVM is critically low on free memory, refuse new
 * work with HTTP 503 instead of risking an OutOfMemoryError that would crash the whole
 * instance. Runs right after {@link ApiCacheControlFilter} (HIGHEST_PRECEDENCE) so the
 * check happens before any request processing / DB work.
 *
 * A small set of paths stay reachable even under pressure: the health probe, public
 * read endpoints, and auth refresh (so a client can recover its session).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class LoadSheddingFilter extends OncePerRequestFilter {

    // Refuse new work when free heap drops below this. Kept as a raw byte count so the
    // check is a single cheap comparison on the hot path.
    private static final long LOW_MEMORY_BYTES = 30L * 1024 * 1024; // 30MB

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!isExempt(request) && Runtime.getRuntime().freeMemory() < LOW_MEMORY_BYTES) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.setHeader("Retry-After", "5");
            response.getWriter().write(
                "{\"error\":\"Server is under high load. Please try again in a moment.\",\"retryAfter\":5}");
            return;
        }

        chain.doFilter(request, response);
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
