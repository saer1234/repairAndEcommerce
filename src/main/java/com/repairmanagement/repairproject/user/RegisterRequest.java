package com.repairmanagement.repairproject.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message="Enter your full name") @Size(max = 150, message="Name must be at most 150 characters") String fullName,
    @NotBlank(message="Enter your email") @Email(message="Enter a valid email address") @Size(max = 190) String email,
    @NotBlank(message="Enter a password") @Size(min = 8, max = 72, message="Password must be between 8 and 72 characters") String password,
    @Size(max = 40) String phone,
    @jakarta.validation.constraints.NotNull RegistrationType accountType
) {}
