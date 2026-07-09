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

    // Secret guest device token — only populated for guest logins. The browser persists
    // this (in place of the old guessable user id) and sends it back to resume the same
    // guest account. Null/omitted for all non-guest responses.
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String guestToken;

    public AuthResponse(String token, UserDto user) {
        this(token, user, null);
    }

    @Data
    @AllArgsConstructor
    public static class UserDto {
        private String id;
        private String fullName;
        private String email;
        private String role;
    }
}
