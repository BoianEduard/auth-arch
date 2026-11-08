package com.saps.auth.api;

public record LoginRequest(String username, String password, RequestContext context) {
}