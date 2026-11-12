package com.repairmanagement.repairproject.company;

/** Published when a membership is changed, to revoke existing support access after commit. */
public record CompanyMembershipChanged(Long companyId, Long userId) {}
