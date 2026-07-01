package com.example.student.controller;

import com.example.student.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    // Genuine bugs / bad input types — never leak internals to the client.
    @ExceptionHandler({ NullPointerException.class, ClassCastException.class })
    public ResponseEntity<?> handleProgrammingError(RuntimeException e) {
        log.error("Unhandled programming error", e);
        return ResponseEntity.status(500).body(Map.of("error", GENERIC_ERROR));
    }

    // Intentional business errors are thrown as RuntimeException across services and
    // carry a user-safe message. Log server-side, return the message to the client.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntime(RuntimeException e) {
        log.warn("Business error: {}", e.getMessage());
        String msg = e.getMessage();
        return ResponseEntity.badRequest().body(Map.of("error", msg != null ? msg : GENERIC_ERROR));
    }

    // Anything else (checked/unknown) — generic 500, details only in server logs.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnknown(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(500).body(Map.of("error", GENERIC_ERROR));
    }
}
