package com.example.student.controller;

import com.example.student.model.User;
import com.example.student.service.CertificateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    /** All certificates the signed-in student has earned. */
    @GetMapping
    public ResponseEntity<?> myCertificates(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(certificateService.getMyCertificates(user.getId()));
    }

    /** A single owned certificate (for the printable/downloadable view). */
    @GetMapping("/{id}")
    public ResponseEntity<?> one(@PathVariable String id, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(certificateService.getOwned(id, user.getId()));
    }

    /** Public verification by code — no login required. */
    @GetMapping("/verify/{code}")
    public ResponseEntity<?> verify(@PathVariable String code) {
        return ResponseEntity.ok(certificateService.verify(code));
    }
}
