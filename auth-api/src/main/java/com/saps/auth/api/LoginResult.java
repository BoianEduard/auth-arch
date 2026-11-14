package com.saps.auth.api;

import java.time.Instant;

public sealed interface LoginResult {

    /**
     * Login completed without an additional challenge.
     */
    record Authenticated(AuthSession session) implements LoginResult {
    }

    /**
     * Password verification succeeded; the client must complete this
     * challenge before receiving a session.
     */
    record MfaRequired(String challengeToken, Instant expiresAt) implements LoginResult {
    }
}