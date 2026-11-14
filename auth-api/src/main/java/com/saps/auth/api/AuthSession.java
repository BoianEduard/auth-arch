package com.saps.auth.api;

/**
 * The credentials issued after successful authentication. Logout requires the
 * complete pair; refresh consumes the refresh token and returns a new pair.
 */
public record AuthSession(AuthToken accessToken, RefreshToken refreshToken) {
}
