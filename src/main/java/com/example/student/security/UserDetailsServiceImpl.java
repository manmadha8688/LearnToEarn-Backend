package com.example.student.security;

import com.example.student.model.User;
import com.example.student.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    // Short-lived cache keyed by email. A single dashboard load fires ~10 parallel
    // authenticated requests, each of which previously read the full User document
    // from Mongo. Caching for a few seconds collapses that to one read while the
    // token-version revocation check in JwtFilter still runs against the cached user.
    // The entry is explicitly evicted on logout and password reset (the only places
    // tokenVersion changes), so revoked tokens stop working immediately on this
    // instance; the tiny TTL bounds any cross-instance staleness.
    private final Cache<String, User> userCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(45))
            .maximumSize(10_000)
            .build();

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User cached = userCache.getIfPresent(email);
        if (cached != null) return cached;
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        userCache.put(email, user);
        return user;
    }

    /** Evict a cached user so the next request reloads fresh — call whenever tokenVersion changes. */
    public void evict(String email) {
        if (email != null) userCache.invalidate(email);
    }
}
