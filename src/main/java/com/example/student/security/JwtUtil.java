package com.example.student.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    // HS256 requires a key of at least 256 bits (32 bytes).
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiry}")
    private long expiry;

    // Fail fast at startup rather than at first login if the secret is missing or weak,
    // so a misconfigured deploy never silently runs with an insecure signing key.
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.isBlank())
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) is not set. Generate with: openssl rand -hex 32");
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES)
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be at least " + MIN_SECRET_BYTES
                    + " bytes for HS256. Generate with: openssl rand -hex 32");
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email, String role, long tokenVersion) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .claim("ver", tokenVersion)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiry))
                .signWith(getKey())
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    /** Token version embedded at issue time; 0 for legacy tokens without the claim. */
    public long extractTokenVersion(String token) {
        Object v = extractClaims(token).get("ver");
        if (v instanceof Number n) return n.longValue();
        return 0L;
    }

    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Short-lived signed state for OAuth redirects (CSRF + user binding). */
    public String createOAuthState(String purpose, String userId, long ttlMs) {
        return createOAuthState(purpose, userId, ttlMs, null);
    }

    /** {@code returnTo} = validated SPA origin to redirect after OAuth (e.g. http://localhost:5173). */
    public String createOAuthState(String purpose, String userId, long ttlMs, String returnTo) {
        return createOAuthState(purpose, userId, ttlMs, returnTo, null);
    }

    /** {@code returnPath} = validated SPA path after OAuth (e.g. /missions/abc or /myprofile). */
    public String createOAuthState(String purpose, String userId, long ttlMs, String returnTo, String returnPath) {
        var builder = Jwts.builder()
                .subject(userId)
                .claim("purpose", purpose)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMs));
        if (returnTo != null && !returnTo.isBlank()) {
            builder.claim("returnTo", returnTo);
        }
        if (returnPath != null && !returnPath.isBlank()) {
            builder.claim("returnPath", returnPath);
        }
        return builder.signWith(getKey()).compact();
    }

    /** Validates OAuth state and returns the embedded user id. */
    public String verifyOAuthState(String purpose, String state) {
        Claims claims = extractClaims(state);
        Object p = claims.get("purpose");
        if (p == null || !purpose.equals(p.toString()))
            throw new JwtException("Invalid OAuth state");
        String userId = claims.getSubject();
        if (userId == null || userId.isBlank())
            throw new JwtException("Invalid OAuth state");
        return userId;
    }

    /** SPA origin embedded at connect time; null when absent or state is invalid. */
    public String extractOAuthReturnTo(String state) {
        try {
            Object r = extractClaims(state).get("returnTo");
            return r != null ? r.toString() : null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** SPA path embedded at connect time; null when absent or state is invalid. */
    public String extractOAuthReturnPath(String state) {
        try {
            Object r = extractClaims(state).get("returnPath");
            return r != null ? r.toString() : null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
