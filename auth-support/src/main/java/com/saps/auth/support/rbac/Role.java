package com.saps.auth.support.rbac;

import com.saps.auth.api.Permission;

import java.util.Set;

public record Role(String name, Set<Permission> permissions) {

    public Role {
        permissions = Set.copyOf(permissions);
    }
}