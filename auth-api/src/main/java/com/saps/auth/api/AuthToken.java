package com.saps.auth.api;

import java.time.Instant;

public record AuthToken(String value, Instant expiresAt) {
}