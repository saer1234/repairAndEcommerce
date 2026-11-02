package com.repairmanagement.repairproject.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(@NotBlank @Email String email, @NotNull MemberRole role) {}
