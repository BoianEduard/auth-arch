package com.saps.auth.support.rbac;

import java.util.Set;

public interface RoleAssignmentRepository {

    Set<String> rolesOf(String username);

    void assign(String username, String roleName);
}