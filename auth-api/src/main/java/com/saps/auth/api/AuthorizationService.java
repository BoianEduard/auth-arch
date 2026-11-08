package com.saps.auth.api;

public interface AuthorizationService {

    boolean isAllowed(Identity who, Permission permission);

    default void require(Identity who, Permission permission) {
        if (!isAllowed(who, permission)) {
            throw new AuthException(AuthFailure.FORBIDDEN,
                    "Missing permission: " + permission.name());
        }
    }
}