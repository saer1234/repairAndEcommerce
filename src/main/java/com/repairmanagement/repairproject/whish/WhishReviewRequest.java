package com.repairmanagement.repairproject.whish;
import jakarta.validation.constraints.*;
public record WhishReviewRequest(@NotBlank @Size(min=3,max=500) String note) {}
