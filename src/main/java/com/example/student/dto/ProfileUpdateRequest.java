package com.example.student.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateRequest {
    @Size(max = 100)
    private String fullName;

    @Size(max = 20)
    private String username;

    @Size(max = 300)
    private String bio;

    @Size(max = 20)
    private String avatarColor;
}
