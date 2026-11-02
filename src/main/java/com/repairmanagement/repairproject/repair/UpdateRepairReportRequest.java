package com.repairmanagement.repairproject.repair;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record UpdateRepairReportRequest(
    @NotBlank @Size(max=4000) String diagnosis,
    @NotBlank @Size(max=4000) String workPerformed
) {}
