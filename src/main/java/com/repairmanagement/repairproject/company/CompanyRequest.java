package com.repairmanagement.repairproject.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
    @NotBlank @Size(max = 180) String name,
    @Email @Size(max = 190) String email,
    @Size(max = 40) String phone,
    @Size(max = 255) String address,
    @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currencyCode
) {}
