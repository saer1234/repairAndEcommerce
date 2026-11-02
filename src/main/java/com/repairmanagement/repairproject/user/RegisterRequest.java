package com.repairmanagement.repairproject.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Size(max = 150) String fullName,
    @NotBlank @Email @Size(max = 190) String email,
    @NotBlank @Size(min = 8, max = 72) String password,
    @Size(max = 40) String phone,
    @jakarta.validation.constraints.NotNull RegistrationType accountType
) {}
