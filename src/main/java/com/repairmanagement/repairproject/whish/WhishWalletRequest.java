package com.repairmanagement.repairproject.whish;
import jakarta.validation.constraints.*;
public record WhishWalletRequest(@NotBlank @Pattern(regexp="\\+?[0-9 ]{7,25}") String phone) {}
