package com.example.student.service;

import com.example.student.exception.ResourceNotFoundException;
import com.example.student.model.User;
import com.example.student.repository.UserRepository;
import com.example.student.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Links a logged-in user's profile to their GitHub account via OAuth (scope: read:user).
 * Stores {@code githubId}, {@code githubLogin}, and {@code githubUrl} — no repo access.
 */
@Service
public class GitHubLinkService {

    private static final Logger log = LoggerFactory.getLogger(GitHubLinkService.class);
    private static final String OAUTH_PURPOSE = "github_link";
    private static final long STATE_TTL_MS = 10 * 60 * 1000L;
    private static final Pattern GITHUB_LOGIN = Pattern.compile("^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$");
    private static final Pattern SAFE_RETURN_PATH = Pattern.compile("^/(myprofile|missions/[a-f0-9]{24})$");

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    // Bounded connect timeout so a slow GitHub endpoint can't tie up the OAuth callback
    // thread indefinitely. Per-request timeout is set on each request below.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .build();

    @Value("${github.client-id:}")
    private String clientId;

    @Value("${github.client-secret:}")
    private String clientSecret;

    /** SPA origin for post-OAuth redirect fallback (e.g. http://localhost:5173 or https://learnforearn.in). */
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
    private String allowedOriginsStr;

    /** Optional fixed backend base (e.g. https://learnforearn.onrender.com). */
    @Value("${app.backend-url:}")
    private String backendBaseUrl;

    @Value("${spring.profiles.active:local}")
    private String activeProfiles;

    public GitHubLinkService(UserRepository userRepository, JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void logRedirectConfig() {
        if (!isProdProfile()) return;
        String fe = normalizeOrigin(frontendUrl);
        if (fe == null || isLocalhost(fe)) {
            log.error("PRODUCTION MISCONFIG: APP_URL must be your public SPA (e.g. https://learnforearn.in), not localhost or the Render URL. Current: {}",
                    frontendUrl);
        } else {
            log.info("GitHub OAuth will redirect to SPA origin: {}", fe);
        }
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    public String buildAuthorizeUrl(User user, HttpServletRequest request, String explicitReturnTo) {
        return buildAuthorizeUrl(user, request, explicitReturnTo, null);
    }

    public String buildAuthorizeUrl(User user, HttpServletRequest request, String explicitReturnTo, String returnPath) {
        if (user == null || "GUEST".equals(user.getRole()))
            throw new IllegalArgumentException("Guest accounts cannot connect GitHub.");
        if (!isConfigured())
            throw new IllegalStateException("GitHub connect is not available right now.");

        String returnOrigin = resolveReturnOrigin(request, explicitReturnTo);
        String safePath = sanitizeReturnPath(returnPath);
        String callback = callbackUrl(request);
        String state = jwtUtil.createOAuthState(OAUTH_PURPOSE, user.getId(), STATE_TTL_MS, returnOrigin, safePath);
        return "https://github.com/login/oauth/authorize"
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(callback)
                + "&scope=read:user"
                + "&allow_signup=false"
                + "&state=" + encode(state);
    }

    public String buildAuthorizeUrl(User user, HttpServletRequest request) {
        return buildAuthorizeUrl(user, request, null);
    }

    public User handleCallback(String code, String state, HttpServletRequest request) {
        if (code == null || code.isBlank())
            throw new IllegalArgumentException("GitHub did not return an authorization code.");
        if (state == null || state.isBlank())
            throw new IllegalArgumentException("Missing OAuth state.");
        if (!isConfigured())
            throw new IllegalStateException("GitHub connect is not available right now.");

        String userId = jwtUtil.verifyOAuthState(OAUTH_PURPOSE, state);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if ("GUEST".equals(user.getRole()))
            throw new IllegalArgumentException("Guest accounts cannot connect GitHub.");

        String callback = callbackUrl(request);
        String accessToken = exchangeCode(code, callback);
        JsonNode ghUser = fetchGitHubUser(accessToken);

        String githubId = ghUser.path("id").asText(null);
        String login = ghUser.path("login").asText(null);
        if (githubId == null || githubId.isBlank() || login == null || login.isBlank())
            throw new IllegalArgumentException("GitHub did not share profile information.");
        if (!GITHUB_LOGIN.matcher(login).matches())
            throw new IllegalArgumentException("GitHub returned an invalid username.");

        Optional<User> existing = userRepository.findByGithubId(githubId);
        if (existing.isPresent() && !existing.get().getId().equals(user.getId()))
            throw new IllegalArgumentException("This GitHub account is already linked to another user.");

        user.setGithubId(githubId);
        user.setGithubLogin(login);
        user.setGithubUrl("https://github.com/" + login);
        addProvider(user, "github");
        return userRepository.save(user);
    }

    public User disconnect(User user) {
        if (user == null || "GUEST".equals(user.getRole()))
            throw new IllegalArgumentException("Guest accounts cannot disconnect GitHub.");
        user.setGithubId(null);
        user.setGithubLogin(null);
        user.setGithubUrl(null);
        removeProvider(user, "github");
        return userRepository.save(user);
    }

    /** Redirect target after OAuth — prefers path stored in {@code state}, else My Profile. */
    public String frontendRedirect(String query, String state) {
        String base = resolveReturnOriginFromState(state);
        String path = resolveReturnPathFromState(state);
        String q = (query == null || query.isBlank()) ? "" : "?" + query;
        if (path == null) {
            return base + "/myprofile" + q + "#social-links";
        }
        if ("/myprofile".equals(path)) {
            return base + "/myprofile" + q + "#social-links";
        }
        return base + path + q;
    }

    public String frontendRedirect(String query) {
        return frontendRedirect(query, null);
    }

    public String frontendErrorRedirect(String reason, String state) {
        return frontendRedirect("github=error&reason=" + encode(reason), state);
    }

    public String frontendErrorRedirect(String reason) {
        return frontendErrorRedirect(reason, null);
    }

    private String resolveReturnPathFromState(String state) {
        String fromState = state != null ? jwtUtil.extractOAuthReturnPath(state) : null;
        return sanitizeReturnPath(fromState);
    }

    /** Accept only in-app relative paths we explicitly allow. */
    private String sanitizeReturnPath(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String path = raw.trim();
        if (!path.startsWith("/")) path = "/" + path;
        if (path.contains("://") || path.startsWith("//")) return null;
        String pathOnly = path.split("[?#]")[0];
        if (!SAFE_RETURN_PATH.matcher(pathOnly).matches()) return null;
        return pathOnly;
    }

    private String resolveReturnOriginFromState(String state) {
        String fromState = state != null ? jwtUtil.extractOAuthReturnTo(state) : null;
        String normalized = normalizeOrigin(fromState);
        if (normalized != null && isAllowedOrigin(normalized)) {
            return normalized;
        }
        if (fromState != null && !fromState.isBlank()) {
            log.warn("GitHub OAuth state returnTo rejected (not in CORS allow-list): {}", fromState);
        }
        return defaultFrontendBase();
    }

    /**
     * Resolve SPA origin for post-OAuth redirect. Priority:
     * 1) explicit {@code returnTo} from the frontend (window.location.origin)
     * 2) Origin / Referer headers
     * 3) {@code APP_URL} / app.frontend-url (never localhost in prod)
     */
    private String resolveReturnOrigin(HttpServletRequest request, String explicitReturnTo) {
        String fromClient = normalizeOrigin(explicitReturnTo);
        if (fromClient != null && isAllowedOrigin(fromClient)) {
            return fromClient;
        }
        if (explicitReturnTo != null && !explicitReturnTo.isBlank()) {
            log.warn("GitHub connect returnTo rejected (not in CORS allow-list): {}", explicitReturnTo);
        }

        String origin = normalizeOrigin(headerFirst(request, "Origin"));
        if (origin == null || origin.isBlank()) {
            String referer = request.getHeader("Referer");
            if (referer != null && !referer.isBlank()) {
                try {
                    URI u = URI.create(referer);
                    if (u.getScheme() != null && u.getHost() != null) {
                        int port = u.getPort();
                        boolean defaultPort = ("http".equalsIgnoreCase(u.getScheme()) && port == 80)
                                || ("https".equalsIgnoreCase(u.getScheme()) && port == 443)
                                || port == -1;
                        origin = normalizeOrigin(u.getScheme() + "://" + u.getHost()
                                + (defaultPort ? "" : ":" + port));
                    }
                } catch (Exception ignored) {
                    // fall through
                }
            }
        }
        if (origin != null && isAllowedOrigin(origin)) {
            return origin;
        }
        return defaultFrontendBase();
    }

    private String defaultFrontendBase() {
        String base = normalizeOrigin(frontendUrl);
        if (base != null && isLocalhost(base)) {
            if (isProdProfile()) {
                log.error("APP_URL is localhost ({}) in production — set APP_URL=https://learnforearn.in on Render", base);
                base = null;
            }
        }
        if (base == null || base.isBlank()) {
            if (isProdProfile()) {
                throw new IllegalStateException(
                        "Post-OAuth redirect is not configured. Set APP_URL to your public site (https://learnforearn.in) "
                                + "and CORS_ALLOWED_ORIGINS to the same origin on Render.");
            }
            return "http://localhost:5173";
        }
        if (!isAllowedOrigin(base) && isProdProfile()) {
            log.warn("APP_URL ({}) is not listed in CORS_ALLOWED_ORIGINS — add it so OAuth redirects stay in sync", base);
        }
        return base;
    }

    private boolean isAllowedOrigin(String origin) {
        String normalized = normalizeOrigin(origin);
        if (normalized == null) return false;
        for (String allowed : allowedOriginsStr.split(",")) {
            String a = normalizeOrigin(allowed.trim());
            if (a != null && a.equalsIgnoreCase(normalized)) return true;
        }
        String fe = normalizeOrigin(frontendUrl);
        if (fe != null && !isLocalhost(fe) && fe.equalsIgnoreCase(normalized)) return true;
        return false;
    }

    /** {@code https://learnforearn.in} — rejects paths, trailing slashes, junk. */
    private static String normalizeOrigin(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String s = trimTrailingSlash(raw.trim());
            if (!s.contains("://")) return null;
            URI u = URI.create(s);
            if (u.getScheme() == null || u.getHost() == null) return null;
            int port = u.getPort();
            boolean defaultPort = port == -1
                    || ("http".equalsIgnoreCase(u.getScheme()) && port == 80)
                    || ("https".equalsIgnoreCase(u.getScheme()) && port == 443);
            return u.getScheme().toLowerCase() + "://" + u.getHost().toLowerCase()
                    + (defaultPort ? "" : ":" + port);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isLocalhost(String origin) {
        try {
            URI u = URI.create(origin);
            String host = u.getHost();
            if (host == null) return false;
            host = host.toLowerCase();
            return host.equals("localhost") || host.equals("127.0.0.1") || host.endsWith(".local");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isProdProfile() {
        if (activeProfiles == null) return false;
        for (String p : activeProfiles.split(",")) {
            if ("prod".equalsIgnoreCase(p.trim())) return true;
        }
        return false;
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) return url;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String exchangeCode(String code, String redirectUri) {
        String body = "client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(redirectUri);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://github.com/login/oauth/access_token"))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("GitHub token exchange failed: HTTP {}", res.statusCode());
                throw new IllegalArgumentException("Could not verify your GitHub account. Please try again.");
            }
            JsonNode json = objectMapper.readTree(res.body());
            String token = json.path("access_token").asText(null);
            if (token == null || token.isBlank())
                throw new IllegalArgumentException("Could not verify your GitHub account. Please try again.");
            return token;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("GitHub token exchange error: {}", e.getMessage());
            throw new IllegalArgumentException("Could not verify your GitHub account. Please try again.");
        }
    }

    private JsonNode fetchGitHubUser(String accessToken) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/user"))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("GitHub /user failed: HTTP {}", res.statusCode());
                throw new IllegalArgumentException("Could not read your GitHub profile. Please try again.");
            }
            return objectMapper.readTree(res.body());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("GitHub /user error: {}", e.getMessage());
            throw new IllegalArgumentException("Could not read your GitHub profile. Please try again.");
        }
    }

    String callbackUrl(HttpServletRequest request) {
        String base = backendBaseUrl != null ? backendBaseUrl.trim() : "";
        if (base.isEmpty()) base = resolveBackendBase(request);
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/api/auth/github/callback";
    }

    private String resolveBackendBase(HttpServletRequest request) {
        String proto = headerFirst(request, "X-Forwarded-Proto");
        if (proto == null || proto.isBlank()) proto = request.getScheme();

        String host = headerFirst(request, "X-Forwarded-Host");
        if (host == null || host.isBlank()) host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            int port = request.getServerPort();
            host = request.getServerName();
            if (("http".equalsIgnoreCase(proto) && port != 80)
                    || ("https".equalsIgnoreCase(proto) && port != 443)) {
                host = host + ":" + port;
            }
        }
        return proto + "://" + host;
    }

    private String headerFirst(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        if (v == null || v.isBlank()) return v;
        int comma = v.indexOf(',');
        return comma >= 0 ? v.substring(0, comma).trim() : v.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void addProvider(User user, String provider) {
        List<String> providers = user.getProviders();
        if (providers == null) {
            providers = new ArrayList<>();
            user.setProviders(providers);
        }
        if (!providers.contains(provider)) providers.add(provider);
    }

    private void removeProvider(User user, String provider) {
        List<String> providers = user.getProviders();
        if (providers != null) providers.remove(provider);
    }
}
