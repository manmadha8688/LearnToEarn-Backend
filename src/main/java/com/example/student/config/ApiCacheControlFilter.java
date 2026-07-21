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
 * Marks every {@code /api/*} response as non-cacheable so no edge/CDN (e.g. Vercel)
 * or browser ever serves stale, user-specific API data. Runs outermost so it writes
 * the final Cache-Control value after the rest of the chain, and only touches
 * Cache-Control / Pragma — CORS and security headers are left untouched.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiCacheControlFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            String uri = request.getRequestURI();
            if (uri != null && uri.startsWith("/api/") && !response.isCommitted()) {
                response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
                response.setHeader("Pragma", "no-cache");
            }
        }
    }
}
