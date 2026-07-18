package com.example.student.exception;

import com.example.student.service.LinkVerificationService;
import com.example.student.service.LinkVerificationService.LinkCheck;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Thrown when one or more URLs fail reachability checks during save. */
public class LinkVerificationException extends RuntimeException {

    private final List<Map<String, Object>> results;

    public LinkVerificationException(List<LinkCheck> checks) {
        super("link_verification_failed");
        this.results = checks.stream()
                .filter(c -> c.status() != LinkVerificationService.Status.VERIFIED)
                .map(LinkVerificationException::toMap)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getResults() {
        return results;
    }

    private static Map<String, Object> toMap(LinkCheck c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", c.url());
        m.put("label", c.label());
        m.put("status", c.status().name().toLowerCase());
        m.put("message", c.message());
        m.put("advice", c.advice());
        return m;
    }
}
