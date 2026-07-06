package com.example.student.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    @Email
    private String email;

    // Cap length so an attacker cannot force expensive BCrypt work with a huge
    // password on every login attempt. BCrypt only uses the first 72 bytes anyway.
    @NotBlank
    @Size(max = 72)
    private String password;
}
