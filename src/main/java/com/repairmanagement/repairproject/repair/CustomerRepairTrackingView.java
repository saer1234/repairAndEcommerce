package com.repairmanagement.repairproject.repair;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Public-safe customer DTO: no internal costs, staff identity or private notes. */
public record CustomerRepairTrackingView(
    Long id, Long companyId, String companyName,
    String deviceType, String brand, String model,
    String issueDescription, RepairStatus status,
    String currencyCode, BigDecimal quotedTotal,
    LocalDateTime createdAt, LocalDateTime completedAt,
    List<CustomerRepairEventView> timeline
) {}
