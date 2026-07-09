package com.example.student.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the real client IP from a request that arrives behind a reverse proxy
 * (Render / Vercel edge).
 *
 * <p>Security note: the {@code X-Forwarded-For} header is a comma-separated chain
 * {@code client, proxy1, proxy2, …} where each hop APPENDS the address it saw. A remote
 * client can pre-populate the header with any value, so the LEFTMOST entry is
 * attacker-controlled and must never be trusted. The trusted infrastructure appends the
 * true peer address on the RIGHT, so the rightmost entry is the trustworthy one. Taking
 * the first entry (the old behaviour) let anyone spoof their IP and bypass every
 * IP-keyed rate limit / lockout.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            // Rightmost non-blank entry = address appended by the closest trusted proxy.
            for (int i = parts.length - 1; i >= 0; i--) {
                String ip = parts[i].trim();
                if (!ip.isEmpty()) return ip;
            }
        }
        return request.getRemoteAddr();
    }
}
