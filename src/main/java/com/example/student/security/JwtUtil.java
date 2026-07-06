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
}
