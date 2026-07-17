package com.example.student.service;

import com.example.student.exception.ResourceNotFoundException;
import com.example.student.model.User;
import com.example.student.repository.UserRepository;
import com.example.student.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${github.client-id:}")
    private String clientId;

    @Value("${github.client-secret:}")
    private String clientSecret;

    @Value("${app.url:http://localhost:5173}")
    private String frontendUrl;

    /** Optional fixed backend base (e.g. https://learnforearn.onrender.com). */
    @Value("${app.backend-url:}")
    private String backendBaseUrl;

    public GitHubLinkService(UserRepository userRepository, JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    public String buildAuthorizeUrl(User user, HttpServletRequest request) {
        if (user == null || "GUEST".equals(user.getRole()))
            throw new IllegalArgumentException("Guest accounts cannot connect GitHub.");
        if (!isConfigured())
            throw new IllegalStateException("GitHub connect is not available right now.");

        String callback = callbackUrl(request);
        String state = jwtUtil.createOAuthState(OAUTH_PURPOSE, user.getId(), STATE_TTL_MS);
        return "https://github.com/login/oauth/authorize"
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(callback)
                + "&scope=read:user"
                + "&state=" + encode(state);
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

    public String frontendRedirect(String query) {
        String base = frontendUrl == null || frontendUrl.isBlank() ? "http://localhost:5173" : frontendUrl.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/myprofile" + (query == null || query.isBlank() ? "" : "?" + query);
    }

    public String frontendErrorRedirect(String reason) {
        return frontendRedirect("github=error&reason=" + encode(reason));
    }

    private String exchangeCode(String code, String redirectUri) {
        String body = "client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(redirectUri);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://github.com/login/oauth/access_token"))
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
