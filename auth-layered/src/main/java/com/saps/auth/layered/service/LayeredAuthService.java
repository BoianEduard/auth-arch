package com.saps.auth.layered.service;

import com.saps.auth.api.AuthException;
import com.saps.auth.api.AuthFailure;
import com.saps.auth.api.AuthSession;
import com.saps.auth.api.AuthToken;
import com.saps.auth.api.Identity;
import com.saps.auth.api.LoginRequest;
import com.saps.auth.api.LoginResult;
import com.saps.auth.api.MfaCapableAuthService;
import com.saps.auth.api.MfaVerifyRequest;
import com.saps.auth.api.RefreshToken;
import com.saps.auth.api.RegisterRequest;
import com.saps.auth.layered.data.User;
import com.saps.auth.layered.data.UserRepository;
import com.saps.auth.support.events.AuthEvent;
import com.saps.auth.support.events.AuthEventPublisher;
import com.saps.auth.support.hashing.PasswordHasher;
import com.saps.auth.support.token.TokenService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.Objects;

public final class LayeredAuthService implements MfaCapableAuthService {

    private static final SecureRandom MFA_RANDOM = new SecureRandom();
    private static final Duration MFA_CHALLENGE_TTL = Duration.ofSeconds(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenService tokens;
    private final AuthEventPublisher events;
    private final BiConsumer<String, String> mfaCodeDelivery;
    private final Clock clock;
    private final ConcurrentMap<String, MfaChallenge> mfaChallenges = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> registrationChallenges = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RefreshSession> refreshSessions = new ConcurrentHashMap<>();
    private final Set<String> revokedAccessTokens = ConcurrentHashMap.newKeySet();

    public LayeredAuthService(UserRepository users,
                              PasswordHasher hasher,
                              TokenService tokens,
                              AuthEventPublisher events,
                              BiConsumer<String, String> mfaCodeDelivery,
                              Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
        this.events = events;
        this.mfaCodeDelivery = Objects.requireNonNull(mfaCodeDelivery, "mfaCodeDelivery");
        this.clock = clock;
    }

    @Override
    public void register(RegisterRequest request) {
        if (request == null
                || isBlank(request.username())
                || request.password() == null
                || request.password().length() < 8) {
            throw new AuthException(AuthFailure.INVALID_INPUT,
                    "Username required, password min 8 chars");
        }

        User user = new User(request.username(),
                hasher.hash(request.password()),
                clock.instant());

        if (!users.saveIfAbsent(user)) {
            throw new AuthException(AuthFailure.USER_ALREADY_EXISTS, "Username taken");
        }
        events.publish(new AuthEvent.UserRegistered(user.username(), clock.instant()));
        LoginResult.MfaRequired registrationChallenge = issueMfaChallenge(user.username());
        registrationChallenges.put(user.username(), registrationChallenge.challengeToken());
    }

    @Override
    public LoginResult login(LoginRequest request) {
        if (request == null || isBlank(request.username()) || request.password() == null) {
            throw new AuthException(AuthFailure.INVALID_INPUT, "Credentials required");
        }

        Optional<User> found = users.findByUsername(request.username());
        boolean credentialsOk = found.isPresent()
                && hasher.verify(request.password(), found.get().passwordHash());

        if (!credentialsOk) {
            events.publish(new AuthEvent.LoginFailed(
                    request.username(), AuthFailure.INVALID_CREDENTIALS, clock.instant()));
            // same error for "no such user" and "wrong password": no user enumeration
            throw new AuthException(AuthFailure.INVALID_CREDENTIALS, "Invalid credentials");
        }

        String registrationChallengeToken =
                registrationChallenges.remove(request.username());
        if (registrationChallengeToken != null) {
            MfaChallenge challenge = mfaChallenges.get(registrationChallengeToken);
            if (challenge != null && clock.instant().isBefore(challenge.expiresAt())) {
                return new LoginResult.MfaRequired(
                        registrationChallengeToken, challenge.expiresAt());
            }
            mfaChallenges.remove(registrationChallengeToken);
        }
        return issueMfaChallenge(request.username());
    }

    @Override
    public AuthSession completeMfa(MfaVerifyRequest request) {
        if (request == null || isBlank(request.challengeToken()) || isBlank(request.code())) {
            throw new AuthException(AuthFailure.INVALID_INPUT, "MFA challenge and code required");
        }

        MfaChallenge challenge = mfaChallenges.get(request.challengeToken());
        if (challenge == null) {
            throw invalidMfaCode();
        }
        if (!clock.instant().isBefore(challenge.expiresAt())) {
            events.publish(new AuthEvent.LoginFailed(
                    challenge.username(), AuthFailure.MFA_CHALLENGE_EXPIRED, clock.instant()));
            throw new AuthException(
                    AuthFailure.MFA_CHALLENGE_EXPIRED, "MFA challenge expired");
        }

        if (!MessageDigest.isEqual(
                challenge.code().getBytes(StandardCharsets.UTF_8),
                request.code().getBytes(StandardCharsets.UTF_8))) {
            events.publish(new AuthEvent.LoginFailed(
                    challenge.username(), AuthFailure.MFA_INVALID_CODE, clock.instant()));
            throw invalidMfaCode();
        }

        if (!mfaChallenges.remove(request.challengeToken(), challenge)) {
            throw invalidMfaCode();
        }

        events.publish(new AuthEvent.LoginSucceeded(challenge.username(), clock.instant()));
        return issueSession(challenge.username());
    }

    @Override
    public LoginResult.MfaRequired resendMfa(String challengeToken) {
        if (isBlank(challengeToken)) {
            throw new AuthException(AuthFailure.INVALID_INPUT, "MFA challenge required");
        }

        MfaChallenge previous = mfaChallenges.get(challengeToken);
        if (previous == null) {
            throw invalidMfaCode();
        }

        Instant expiresAt = clock.instant().plus(MFA_CHALLENGE_TTL);
        MfaChallenge renewed = new MfaChallenge(
                previous.username(), generateMfaCode(), expiresAt);
        if (!mfaChallenges.replace(challengeToken, previous, renewed)) {
            throw invalidMfaCode();
        }
        events.publish(new AuthEvent.MfaCodeResent(previous.username(), clock.instant()));
        mfaCodeDelivery.accept(previous.username(), renewed.code());
        return new LoginResult.MfaRequired(challengeToken, expiresAt);
    }

    @Override
    public AuthSession refresh(RefreshToken refreshToken) {
        if (refreshToken == null || isBlank(refreshToken.value())) {
            throw new AuthException(AuthFailure.INVALID_INPUT, "Refresh token required");
        }

        RefreshSession previous = refreshSessions.get(refreshToken.value());
        if (previous == null) {
            throw invalidRefreshToken();
        }
        if (!clock.instant().isBefore(previous.expiresAt())) {
            refreshSessions.remove(refreshToken.value(), previous);
            throw new AuthException(AuthFailure.REFRESH_TOKEN_EXPIRED, "Refresh token expired");
        }
        if (!refreshSessions.remove(refreshToken.value(), previous)) {
            throw invalidRefreshToken();
        }

        revokedAccessTokens.add(previous.accessToken());
        AuthSession refreshed = issueSession(previous.username());
        events.publish(new AuthEvent.SessionRefreshed(previous.username(), clock.instant()));
        return refreshed;
    }

    @Override
    public void logout(AuthSession session) {
        if (session == null
                || session.accessToken() == null
                || isBlank(session.accessToken().value())
                || session.refreshToken() == null
                || isBlank(session.refreshToken().value())) {
            throw new AuthException(AuthFailure.INVALID_INPUT, "Session required");
        }

        RefreshSession refreshSession = refreshSessions.get(session.refreshToken().value());
        String username;
        if (refreshSession != null
                && !refreshSession.accessToken().equals(session.accessToken().value())) {
            throw invalidRefreshToken();
        }
        if (refreshSession == null) {
            if (revokedAccessTokens.contains(session.accessToken().value())) {
                return;
            }
            username = tokens.verify(session.accessToken().value()).username();
        } else {
            username = refreshSession.username();
        }
        if (refreshSession != null) {
            refreshSessions.remove(session.refreshToken().value(), refreshSession);
        }
        revokedAccessTokens.add(session.accessToken().value());
        events.publish(new AuthEvent.SessionLoggedOut(username, clock.instant()));
    }

    @Override
    public Identity verify(String token) {
        Identity identity = tokens.verify(token);
        if (revokedAccessTokens.contains(token)) {
            throw new AuthException(AuthFailure.INVALID_TOKEN, "Token revoked");
        }
        return identity;
    }

    private AuthSession issueSession(String username) {
        AuthToken accessToken = tokens.issue(username);
        Instant refreshExpiresAt = clock.instant().plus(REFRESH_TOKEN_TTL);
        RefreshToken refreshToken = new RefreshToken(UUID.randomUUID().toString(), refreshExpiresAt);
        refreshSessions.put(refreshToken.value(),
                new RefreshSession(username, accessToken.value(), refreshExpiresAt));
        return new AuthSession(accessToken, refreshToken);
    }

    private LoginResult.MfaRequired issueMfaChallenge(String username) {
        Instant expiresAt = clock.instant().plus(MFA_CHALLENGE_TTL);
        String challengeToken = UUID.randomUUID().toString();
        String code = generateMfaCode();
        mfaChallenges.put(challengeToken, new MfaChallenge(username, code, expiresAt));
        events.publish(new AuthEvent.MfaChallengeIssued(username, clock.instant()));
        mfaCodeDelivery.accept(username, code);
        return new LoginResult.MfaRequired(challengeToken, expiresAt);
    }

    private static String generateMfaCode() {
        return String.format(Locale.ROOT, "%06d", MFA_RANDOM.nextInt(1_000_000));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static AuthException invalidMfaCode() {
        return new AuthException(AuthFailure.MFA_INVALID_CODE, "Invalid or expired MFA challenge");
    }

    private static AuthException invalidRefreshToken() {
        return new AuthException(AuthFailure.INVALID_REFRESH_TOKEN, "Invalid refresh token");
    }

    private record MfaChallenge(String username, String code, Instant expiresAt) {
    }

    private record RefreshSession(String username, String accessToken, Instant expiresAt) {
    }
}