package com.example.student.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    // Token is used server-side to set the httpOnly cookie, but never serialized to the
    // client body — auth is cookie-only, so exposing it in JSON would defeat httpOnly.
    @JsonIgnore
    private String token;
    private UserDto user;

    @Data
    @AllArgsConstructor
    public static class UserDto {
        private String id;
        private String fullName;
        private String email;
        private String role;
    }
}
