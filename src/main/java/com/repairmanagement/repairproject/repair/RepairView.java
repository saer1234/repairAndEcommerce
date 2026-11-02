package com.repairmanagement.repairproject.repair;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepairView(
    Long id, Long companyId, String currencyCode, Long customerId,
    String clientName, String clientPhone,
    Long assignedEmployeeId, String deviceType, String brand, String model,
    String issueDescription, String diagnosis, String workPerformed, RepairStatus status,
    BigDecimal laborCharge, BigDecimal partsCharge, BigDecimal totalCharge,
    BigDecimal laborCost, BigDecimal partsCost, BigDecimal estimatedGrossProfit,
    LocalDateTime createdAt, LocalDateTime completedAt,
    List<RepairPartView> parts, List<RepairUpdateView> updates
) {}
