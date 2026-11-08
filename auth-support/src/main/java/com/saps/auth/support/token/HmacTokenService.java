package com.saps.auth.support.token;

import com.saps.auth.api.AuthException;
import com.saps.auth.api.AuthFailure;
import com.saps.auth.api.AuthToken;
import com.saps.auth.api.Identity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

public final class HmacTokenService implements TokenService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final Duration ttl;
    private final Clock clock;

    public HmacTokenService(byte[] secret, Duration ttl, Clock clock) {
        this.secret = secret.clone();
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public AuthToken issue(String username) {
        Instant expiresAt = clock.instant().plus(ttl);
        String payload = username + "|" + expiresAt.getEpochSecond();
        String body = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = ENCODER.encodeToString(sign(body));
        return new AuthToken(body + "." + signature, expiresAt);
    }

    @Override
    public Identity verify(String token) {
        if (token == null) {
            throw invalid();
        }
        int dot = token.indexOf('.');
        if (dot < 0) {
            throw invalid();
        }
        String body = token.substring(0, dot);

        String payload;
        try {
            byte[] signature = DECODER.decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(signature, sign(body))) {
                throw invalid();
            }
            payload = new String(DECODER.decode(body), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw invalid(); // not valid base64url
        }

        int bar = payload.lastIndexOf('|');
        if (bar < 0) {
            throw invalid();
        }
        Instant expiresAt;
        try {
            expiresAt = Instant.ofEpochSecond(Long.parseLong(payload.substring(bar + 1)));
        } catch (NumberFormatException e) {
            throw invalid();
        }
        if (!clock.instant().isBefore(expiresAt)) {
            throw new AuthException(AuthFailure.TOKEN_EXPIRED, "Token expired");
        }
        return new Identity(payload.substring(0, bar), expiresAt);
    }

    private byte[] sign(String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AuthException invalid() {
        return new AuthException(AuthFailure.INVALID_TOKEN, "Invalid token");
    }
}