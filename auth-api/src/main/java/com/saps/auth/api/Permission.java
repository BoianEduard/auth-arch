package com.saps.auth.api;

public record Permission(String name) {

    public Permission {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Permission name must not be blank");
        }
    }

    public static Permission of(String name) {
        return new Permission(name);
    }
}