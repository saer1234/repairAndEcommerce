package com.repairmanagement.repairproject.user;

import java.time.Instant;
public record LoginView(String accessToken, String tokenType, Instant expiresAt, UserView user) {}
