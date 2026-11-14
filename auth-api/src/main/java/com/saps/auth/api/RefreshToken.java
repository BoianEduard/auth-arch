package com.saps.auth.api;

import java.time.Instant;

/**
 * Opaque credential used once to obtain a rotated authentication session.
 */
public record RefreshToken(String value, Instant expiresAt) {
}
