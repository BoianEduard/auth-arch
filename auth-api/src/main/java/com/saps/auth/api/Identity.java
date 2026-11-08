package com.saps.auth.api;

import java.time.Instant;

public record Identity(String username, Instant expiresAt) {
}