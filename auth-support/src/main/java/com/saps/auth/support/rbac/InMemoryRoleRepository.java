package com.saps.auth.support.rbac;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRoleRepository implements RoleRepository {

    private final ConcurrentHashMap<String, Role> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Role> findByName(String roleName) {
        return Optional.ofNullable(store.get(roleName));
    }

    @Override
    public void save(Role role) {
        store.put(role.name(), role);
    }
}