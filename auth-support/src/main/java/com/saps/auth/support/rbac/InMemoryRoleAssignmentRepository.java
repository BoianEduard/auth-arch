package com.saps.auth.support.rbac;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRoleAssignmentRepository implements RoleAssignmentRepository {

    private final ConcurrentHashMap<String, Set<String>> store = new ConcurrentHashMap<>();

    @Override
    public Set<String> rolesOf(String username) {
        return Set.copyOf(store.getOrDefault(username, Set.of()));
    }

    @Override
    public void assign(String username, String roleName) {
        store.computeIfAbsent(username, key -> ConcurrentHashMap.newKeySet()).add(roleName);
    }
}