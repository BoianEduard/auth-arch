package com.saps.auth.support.rbac;

import java.util.Optional;

public interface RoleRepository {

    Optional<Role> findByName(String roleName);

    void save(Role role);
}