package com.saps.auth.support.token;

import com.saps.auth.api.AuthToken;
import com.saps.auth.api.Identity;

public interface TokenService {

    AuthToken issue(String username);

    /** @throws com.saps.auth.api.AuthException INVALID_TOKEN or TOKEN_EXPIRED */
    Identity verify(String token);
}