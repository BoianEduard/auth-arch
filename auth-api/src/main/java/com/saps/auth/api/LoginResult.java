package com.saps.auth.api;

import java.time.Instant;

public sealed interface LoginResult {

    record Authenticated(AuthToken token) implements LoginResult {
    }

    record MfaRequired(String challengeToken, Instant expiresAt) implements LoginResult {
    }
}