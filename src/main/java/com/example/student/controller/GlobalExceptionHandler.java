package com.example.student.controller;

import com.example.student.exception.LinkVerificationException;
import com.example.student.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String GENERIC_ERROR = "Something went wrong. Please try again.";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe ->
            errors.put(fe.getField(), fe.getDefaultMessage())
        );
        return ResponseEntity.badRequest().body(Map.of("errors", errors));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<?> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(LinkVerificationException.class)
    public ResponseEntity<?> handleLinkVerification(LinkVerificationException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "link_verification_failed");
        body.put("results", e.getResults());
        return ResponseEntity.status(422).body(body);
    }

    /** Resume/mission services use ResponseStatusException for 404/409 with user-safe reasons. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatus(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String msg = e.getReason();
        if (msg == null || msg.isBlank()) msg = GENERIC_ERROR;
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }

    // Unmapped route / missing static resource. Without this, the catch-all below
    // turned every unknown /api/* path into a 500 — polluting error monitoring with
    // false alarms. Return a proper 404 instead.
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResource(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        return ResponseEntity.status(404).body(Map.of("error", "Not found"));
    }

    // Genuine bugs / bad input types — never leak internals to the client.
    @ExceptionHandler({ NullPointerException.class, ClassCastException.class })
    public ResponseEntity<?> handleProgrammingError(RuntimeException e) {
        log.error("Unhandled programming error", e);
        return ResponseEntity.status(500).body(Map.of("error", GENERIC_ERROR));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Business error: {}", e.getMessage());
        String msg = e.getMessage();
        return ResponseEntity.badRequest().body(Map.of("error", msg != null && !msg.isBlank() ? msg : GENERIC_ERROR));
    }

    // Intentional business errors are thrown as RuntimeException across services and
    // carry a user-safe message. Log server-side, return the message to the client.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntime(RuntimeException e) {
        log.warn("Business error: {}", e.getMessage());
        String msg = e.getMessage();
        return ResponseEntity.badRequest().body(Map.of("error", msg != null && !msg.isBlank() ? msg : GENERIC_ERROR));
    }

    // Anything else (checked/unknown) — generic 500, details only in server logs.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnknown(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(500).body(Map.of("error", GENERIC_ERROR));
    }
}
