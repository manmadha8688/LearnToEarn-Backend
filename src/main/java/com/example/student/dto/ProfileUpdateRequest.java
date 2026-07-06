package com.example.student.dto;

import lombok.Data;

@Data
public class ProfileUpdateRequest {
    private String fullName;
    private String username;
    private String bio;
    private String avatarColor;
}
