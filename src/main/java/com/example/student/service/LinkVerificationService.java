package com.example.student.service;

import com.example.student.exception.LinkVerificationException;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks whether user-supplied http(s) links are reachable before save.
 * Returns VERIFIED, RETRY (cold start / slow host), or UNVERIFIABLE.
 */
@Service
public class LinkVerificationService {

    public enum Status { VERIFIED, RETRY, UNVERIFIABLE }

    public record LinkTarget(String label, String url) {}

    public record LinkCheck(String url, String label, Status status, String message, String advice) {}

    private static final Pattern URL = Pattern.compile("^https?://[^\\s/$.?#].[^\\s]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GITHUB_REPO = Pattern.compile(
            "^https?://(?:www\\.)?github\\.com/([a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38})/([a-zA-Z0-9._-]+)(?:/.*)?(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COLD_START_HOST = Pattern.compile(
            "(^|\\.)((on)?render\\.com|railway\\.app|fly\\.dev|herokuapp\\.com)$",
            Pattern.CASE_INSENSITIVE);
    /** Personal/org LinkedIn paths only — rejects typo domains like linedin.com. */
    private static final Pattern LINKEDIN_PATH = Pattern.compile(
            "^/(in|company|school|pub)/[a-zA-Z0-9\\-_%]+/?$",
            Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** Verify all targets; throws {@link LinkVerificationException} unless skip is true. */
    public void requireVerified(List<LinkTarget> targets, boolean skip) {
        if (skip || targets == null || targets.isEmpty()) return;
        List<LinkCheck> checks = verifyAll(targets);
        boolean allOk = checks.stream().allMatch(c -> c.status() == Status.VERIFIED);
        if (!allOk) throw new LinkVerificationException(checks);
    }

    public List<LinkCheck> verifyAll(List<LinkTarget> targets) {
        List<LinkCheck> out = new ArrayList<>();
        if (targets == null) return out;
        Map<String, LinkCheck> seen = new LinkedHashMap<>();
        for (LinkTarget t : targets) {
            if (t == null || t.url() == null || t.url().isBlank()) continue;
            String normalized = normalizeUrl(t.url().trim());
            if (normalized == null) {
                out.add(new LinkCheck(t.url(), t.label(), Status.UNVERIFIABLE,
                        "Enter a full URL starting with http:// or https://",
                        "Fix the URL format, then save again."));
                continue;
            }
            String key = normalized.toLowerCase(Locale.ROOT);
            if (seen.containsKey(key)) {
                out.add(seen.get(key));
                continue;
            }
            LinkCheck check = verifyOne(normalized, t.label());
            seen.put(key, check);
            out.add(check);
        }
        return out;
    }

    /** True when URL is on linkedin.com (not lookalike domains) with a profile-style path. */
    public boolean isValidLinkedInProfileUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host == null || !isLinkedInHost(host)) return false;
            String path = uri.getPath();
            return path != null && LINKEDIN_PATH.matcher(path).matches();
        } catch (Exception e) {
            return false;
        }
    }

    /** Normalize to https + lowercase host + trimmed profile path (no query/fragment). */
    public String canonicalLinkedInUrl(String url) {
        if (url == null || url.isBlank()) return null;
        URI uri = URI.create(url.trim());
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if ("linkedin.com".equals(host)) host = "www.linkedin.com";
        String path = uri.getPath();
        if (path != null && !path.isEmpty()) path = path.toLowerCase(Locale.ROOT);
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return "https://" + host + path;
    }

    /** Normalize portfolio / website URLs for storage and duplicate checks. */
    public String canonicalPortfolioUrl(String url) {
        if (url == null || url.isBlank()) return null;
        String normalized = normalizeUrl(url.trim());
        if (normalized == null) return null;
        try {
            URI uri = URI.create(normalized);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host == null || host.isBlank()) return null;
            String path = uri.getPath() != null ? uri.getPath() : "";
            if (!path.isEmpty()) path = path.toLowerCase(Locale.ROOT);
            if (path.endsWith("/") && path.length() > 1) path = path.substring(0, path.length() - 1);
            int port = uri.getPort();
            String portPart = (port > 0 && port != 443 && port != 80) ? ":" + port : "";
            return "https://" + host + portPart + path;
        } catch (Exception e) {
            return normalized;
        }
    }

    private LinkCheck verifyOne(String url, String label) {
        if (!isSafePublicUrl(url)) {
            return new LinkCheck(url, label, Status.UNVERIFIABLE,
                    "That link cannot be verified.",
                    "Use a public http(s) URL — not localhost or a private network address.");
        }

        ParsedRepo gh = parseGitHubRepo(url);
        if (gh != null) {
            return verifyGitHubRepo(url, label, gh);
        }

        if ("LinkedIn".equalsIgnoreCase(label)) {
            if (!isValidLinkedInProfileUrl(url)) {
                return new LinkCheck(url, label, Status.UNVERIFIABLE,
                        "That is not a valid LinkedIn profile URL.",
                        "Use linkedin.com — e.g. https://www.linkedin.com/in/your-name (check spelling).");
            }
            // LinkedIn blocks automated HTTP checks (403) even for real profiles — format validation is sufficient.
            return verified(canonicalLinkedInUrl(url), label);
        }

        return verifyHttp(url, label);
    }

    private LinkCheck verifyGitHubRepo(String url, String label, ParsedRepo parsed) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + parsed.owner() + "/" + parsed.repo()))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "LearnForEarn-ARISE")
                    .timeout(java.time.Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() == 200) {
                return verified(url, label);
            }
            if (res.statusCode() == 404) {
                return new LinkCheck(url, label, Status.UNVERIFIABLE,
                        "We couldn't find that GitHub repository.",
                        "Check the owner and repo name, make the repo public, then try again.");
            }
            if (res.statusCode() == 403) {
                return new LinkCheck(url, label, Status.UNVERIFIABLE,
                        "We couldn't verify that GitHub repository right now.",
                        "If the repo is private, make it public or save without verification after confirming it opens in your browser.");
            }
            return new LinkCheck(url, label, Status.UNVERIFIABLE,
                    "We couldn't verify that GitHub repository.",
                    "Open the link in your browser. If it works, save without verification.");
        } catch (Exception e) {
            return new LinkCheck(url, label, Status.RETRY,
                    "We couldn't reach GitHub to verify this repository.",
                    "Check your connection and try again in a moment.");
        }
    }

    private LinkCheck verifyHttp(String url, String label) {
        String host = hostOf(url);
        boolean coldStart = host != null && COLD_START_HOST.matcher(host).find();
        boolean linkedIn = isLinkedInHost(host);

        int[] timeouts = { 12, 25 };
        Status lastStatus = Status.UNVERIFIABLE;
        String lastMessage = "We couldn't verify this link.";
        String lastAdvice = "Open the link in your browser. If it loads, save without verification.";

        for (int attempt = 0; attempt < timeouts.length; attempt++) {
            HttpProbe probe = probe(url, timeouts[attempt]);
            if (probe.ok()) {
                return verified(url, label);
            }
            lastStatus = probe.status();
            lastMessage = probe.message();
            lastAdvice = probe.advice();

            if (probe.status() == Status.VERIFIED) {
                return verified(url, label);
            }
            if (probe.status() == Status.RETRY && attempt < timeouts.length - 1) {
                continue;
            }
            break;
        }

        if (linkedIn && lastStatus == Status.UNVERIFIABLE) {
            lastAdvice = "LinkedIn often blocks automated checks. Open your profile in a browser to confirm, "
                    + "then save without verification if it works for you.";
        } else if (coldStart && lastStatus != Status.VERIFIED) {
            lastStatus = Status.RETRY;
            lastMessage = "Your hosted app may still be waking up (cold start).";
            lastAdvice = "Open the link in your browser and wait until the page loads fully, then tap Try again. "
                    + "Free tiers on Render/Railway can take up to a minute on first visit.";
        }

        return new LinkCheck(url, label, lastStatus, lastMessage, lastAdvice);
    }

    private HttpProbe probe(String url, int timeoutSec) {
        try {
            HttpRequest head = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(timeoutSec))
                    .header("User-Agent", "LearnForEarn-ARISE-LinkCheck/1.0")
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> res = httpClient.send(head, HttpResponse.BodyHandlers.discarding());
            return classifyResponse(url, res.statusCode(), false);
        } catch (Exception headErr) {
            try {
                HttpRequest get = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(java.time.Duration.ofSeconds(timeoutSec))
                        .header("User-Agent", "LearnForEarn-ARISE-LinkCheck/1.0")
                        .GET()
                        .build();
                HttpResponse<Void> res = httpClient.send(get, HttpResponse.BodyHandlers.discarding());
                return classifyResponse(url, res.statusCode(), false);
            } catch (Exception getErr) {
                String host = hostOf(url);
                boolean cold = host != null && COLD_START_HOST.matcher(host).find();
                if (isTimeout(getErr) && cold) {
                    return new HttpProbe(Status.RETRY,
                            "The server is taking too long to respond — it may be waking up.",
                            "Wait a moment, open the link in your browser, then try again.");
                }
                if (isTimeout(getErr)) {
                    return new HttpProbe(Status.UNVERIFIABLE,
                            "The link timed out before we could reach it.",
                            "Check the URL and your deployment. If it opens in your browser, save without verification.");
                }
                return new HttpProbe(Status.UNVERIFIABLE,
                        "We couldn't reach this link.",
                        "Check spelling and that the site is deployed. If it works in your browser, save without verification.");
            }
        }
    }

    private HttpProbe classifyResponse(String url, int code, boolean linkedInOverride) {
        if (code >= 200 && code < 400) {
            return new HttpProbe(Status.VERIFIED, "", "");
        }
        if (code >= 300 && code < 400) {
            return new HttpProbe(Status.UNVERIFIABLE,
                    "The link redirected — we could not verify the final destination.",
                    "Open the link in your browser. If it loads, save without verification.");
        }
        if (code == 404) {
            return new HttpProbe(Status.UNVERIFIABLE,
                    "That page was not found — the link may be wrong or removed.",
                    "Fix the URL or deploy the site, then try again.");
        }
        if (code == 403 || code == 401) {
            String host = hostOf(url);
            boolean linkedIn = linkedInOverride || isLinkedInHost(host);
            return new HttpProbe(Status.UNVERIFIABLE,
                    linkedIn ? "LinkedIn blocked our automated check." : "The site blocked our automated check.",
                    linkedIn
                            ? "Open your LinkedIn profile in a browser. If it loads, save without verification."
                            : "Open the link in your browser. If it loads, save without verification.");
        }
        if (code == 502 || code == 503 || code == 504 || code == 429) {
            String host = hostOf(url);
            boolean cold = host != null && COLD_START_HOST.matcher(host).find();
            return new HttpProbe(cold ? Status.RETRY : Status.UNVERIFIABLE,
                    cold ? "The app returned a temporary error — it may still be starting." : "The site returned a temporary error.",
                    cold
                            ? "Open the link in your browser, wait for it to load, then try again."
                            : "Try again shortly or save without verification if the link works for you.");
        }
        return new HttpProbe(Status.UNVERIFIABLE,
                "The link returned an unexpected response.",
                "If the link works for you, save without verification.");
    }

    private LinkCheck verified(String url, String label) {
        return new LinkCheck(url, label, Status.VERIFIED, "Link verified.", "");
    }

    private record HttpProbe(Status status, String message, String advice) {
        boolean ok() { return status == Status.VERIFIED; }
    }

    private record ParsedRepo(String owner, String repo) {}

    private ParsedRepo parseGitHubRepo(String raw) {
        Matcher m = GITHUB_REPO.matcher(raw);
        if (!m.matches()) return null;
        String repo = m.group(2);
        if (repo.endsWith(".git")) repo = repo.substring(0, repo.length() - 4);
        if ("settings".equalsIgnoreCase(repo) || "pull".equalsIgnoreCase(repo)) return null;
        return new ParsedRepo(m.group(1), repo);
    }

    /** Adds https:// when the user omitted the scheme (common on resume project links). */
    public String normalizeUrl(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim();
        if (!v.matches("(?i)^https?://.*")) {
            v = "https://" + v;
        }
        return URL.matcher(v).matches() ? v : null;
    }

    private boolean isSafePublicUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) return false;
            host = host.toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.endsWith(".localhost") || host.equals("0.0.0.0")) {
                return false;
            }
            if (host.equals("metadata.google.internal") || host.contains("169.254.169.254")) {
                return false;
            }
            InetAddress addr = InetAddress.getByName(host);
            return !(addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                    || addr.isLinkLocalAddress() || addr.isSiteLocalAddress());
        } catch (Exception e) {
            return false;
        }
    }

    private String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    /** Exact linkedin.com host only — linedin.com and linkedin.com.evil.com fail. */
    private boolean isLinkedInHost(String host) {
        if (host == null || host.isBlank()) return false;
        host = host.toLowerCase(Locale.ROOT);
        if (host.equals("linkedin.com") || host.equals("www.linkedin.com")) return true;
        if (!host.endsWith(".linkedin.com")) return false;
        String prefix = host.substring(0, host.length() - ".linkedin.com".length());
        return prefix.matches("[a-z]{2}|www");
    }

    private boolean isTimeout(Exception e) {
        if (e instanceof java.net.http.HttpTimeoutException) return true;
        String msg = e.getMessage();
        return msg != null && msg.toLowerCase(Locale.ROOT).contains("timed out");
    }
}
