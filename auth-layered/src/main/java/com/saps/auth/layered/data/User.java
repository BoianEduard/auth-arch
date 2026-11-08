package com.saps.auth.layered.data;

import java.time.Instant;

public record User(String username, String passwordHash, Instant createdAt) {
}