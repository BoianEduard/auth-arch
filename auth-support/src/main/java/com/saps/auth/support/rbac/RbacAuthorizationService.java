package com.saps.auth.support.rbac;

import com.saps.auth.api.AuthorizationService;
import com.saps.auth.api.Identity;
import com.saps.auth.api.Permission;

public final class RbacAuthorizationService implements AuthorizationService {

    private final RoleRepository roles;
    private final RoleAssignmentRepository assignments;

    public RbacAuthorizationService(RoleRepository roles, RoleAssignmentRepository assignments) {
        this.roles = roles;
        this.assignments = assignments;
    }

    @Override
    public boolean isAllowed(Identity who, Permission permission) {
        if (who == null || permission == null) {
            return false; // deny by default
        }
        for (String roleName : assignments.rolesOf(who.username())) {
            boolean granted = roles.findByName(roleName)
                    .map(role -> role.permissions().contains(permission))
                    .orElse(false);
            if (granted) {
                return true;
            }
        }
        return false;
    }
}