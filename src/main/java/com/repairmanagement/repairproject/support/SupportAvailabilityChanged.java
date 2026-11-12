package com.repairmanagement.repairproject.support;

/** Dispatched only after a committed presence change. */
public record SupportAvailabilityChanged(Long companyId, long availableAgents) {}
