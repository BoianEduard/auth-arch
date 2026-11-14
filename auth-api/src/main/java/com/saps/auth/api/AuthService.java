package com.saps.auth.api;

/**
 * Core authentication operations shared by every implementation variant.
 * A refresh token is single-use: refreshing invalidates it and returns a new
 * session. Logout is idempotent and invalidates the session's access and
 * refresh tokens.
 */
public interface AuthService {

    /**
     * Registers an account. Username must not be blank, password must contain
     * at least eight characters, and usernames must be unique.
     *
     * @throws AuthException INVALID_INPUT or USER_ALREADY_EXISTS
     */
    void register(RegisterRequest request);

    /**
     * Authenticates credentials. Implementations without MFA return an
     * authenticated session; MFA-capable implementations may instead return
     * an MFA challenge.
     *
     * @throws AuthException INVALID_INPUT or INVALID_CREDENTIALS
     */
    LoginResult login(LoginRequest request);

    /**
     * Exchanges an unexpired refresh token for a new session and rotates the
     * refresh token.
     *
     * @throws AuthException INVALID_INPUT, INVALID_REFRESH_TOKEN, or
     *                       REFRESH_TOKEN_EXPIRED
     */
    AuthSession refresh(RefreshToken refreshToken);

    /**
     * Invalidates both credentials in the session. Repeating logout for the
     * same session has no effect.
     *
     * @throws AuthException INVALID_INPUT or INVALID_REFRESH_TOKEN
     */
    void logout(AuthSession session);

    /**
     * Verifies an access token and returns its identity.
     *
     * @throws AuthException INVALID_TOKEN (including a revoked token) or
     *                       TOKEN_EXPIRED
     */
    Identity verify(String token);
}