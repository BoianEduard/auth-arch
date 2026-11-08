package com.saps.auth.api;

public record MfaVerifyRequest(String challengeToken, String code) {
}