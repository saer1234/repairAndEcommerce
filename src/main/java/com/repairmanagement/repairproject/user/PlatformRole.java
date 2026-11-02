package com.repairmanagement.repairproject.user;

// USER is retained for legacy accounts that have not been migrated yet.
// SUPER_ADMIN is provisioned privately and never available during public sign-up.
public enum PlatformRole { USER, OWNER, CLIENT, SUPER_ADMIN }
