package com.saps.auth.layered.service;

import com.saps.auth.api.AuthException;
import com.saps.auth.api.AuthFailure;
import com.saps.auth.api.AuthService;
import com.saps.auth.api.AuthToken;
import com.saps.auth.api.Identity;
import com.saps.auth.api.LoginRequest;
import com.saps.auth.api.LoginResult;
import com.saps.auth.api.MfaVerifyRequest;
import com.saps.auth.api.RegisterRequest;
import com.saps.auth.layered.data.User;
import com.saps.auth.layered.data.UserRepository;
import com.saps.auth.support.events.AuthEvent;
import com.saps.auth.support.events.AuthEventPublisher;
import com.saps.auth.support.hashing.PasswordHasher;
import com.saps.auth.support.token.TokenService;

import java.time.Clock;
import java.util.Optional;

public final class LayeredAuthService implements AuthService {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenService tokens;
    private final AuthEventPublisher events;
    private final Clock clock;

    public LayeredAuthService(UserRepository users,
                              PasswordHasher hasher,
                              TokenService tokens,
                              AuthEventPublisher events,
                              Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
        this.events = events;
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

        events.publish(new AuthEvent.LoginSucceeded(request.username(), clock.instant()));
        return new LoginResult.Authenticated(tokens.issue(request.username()));
    }

    @Override
    public AuthToken completeMfa(MfaVerifyRequest request) {
        throw new AuthException(AuthFailure.MFA_NOT_ENABLED, "MFA not implemented yet (CR2)");
    }

    @Override
    public Identity verify(String token) {
        return tokens.verify(token);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}