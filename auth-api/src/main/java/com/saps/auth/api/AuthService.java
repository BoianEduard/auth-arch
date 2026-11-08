package com.saps.auth.api;

public interface AuthService {

    void register(RegisterRequest request);

    LoginResult login(LoginRequest request);

    AuthToken completeMfa(MfaVerifyRequest request);

    Identity verify(String token);
}