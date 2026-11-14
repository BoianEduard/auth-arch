package com.saps.auth.api;

/**
 * Optional capability for implementations that support multi-factor login.
 * When an implementation requires MFA, {@link AuthService#login(LoginRequest)}
 * returns {@link LoginResult.MfaRequired}; completing the challenge returns a
 * session with access and refresh tokens.
 */
public interface MfaCapableAuthService extends AuthService {

    /**
     * Completes an unexpired challenge. A challenge can be used only once.
     *
     * @throws AuthException INVALID_INPUT, MFA_INVALID_CODE, or
     *                       MFA_CHALLENGE_EXPIRED
     */
    AuthSession completeMfa(MfaVerifyRequest request);

    /**
     * Reissues a code and expiry for an existing challenge.
     *
     * @throws AuthException INVALID_INPUT or MFA_INVALID_CODE
     */
    LoginResult.MfaRequired resendMfa(String challengeToken);
}
