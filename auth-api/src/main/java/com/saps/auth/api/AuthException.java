package com.saps.auth.api;

public class AuthException extends RuntimeException {

    private final AuthFailure failure;

    public AuthException(AuthFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public AuthFailure failure() {
        return failure;
    }
}