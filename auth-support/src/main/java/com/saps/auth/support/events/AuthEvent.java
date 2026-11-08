package com.saps.auth.support.events;

import com.saps.auth.api.AuthFailure;

import java.time.Instant;

public sealed interface AuthEvent {

    Instant occurredAt();

    record UserRegistered(String username, Instant occurredAt) implements AuthEvent {
    }

    record LoginSucceeded(String username, Instant occurredAt) implements AuthEvent {
    }

    record LoginFailed(String username, AuthFailure reason, Instant occurredAt) implements AuthEvent {
    }
}