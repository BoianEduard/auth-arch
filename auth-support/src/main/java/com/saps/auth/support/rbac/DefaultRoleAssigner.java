package com.saps.auth.support.rbac;

import com.saps.auth.support.events.AuthEvent;
import com.saps.auth.support.events.AuthEventListener;

public final class DefaultRoleAssigner implements AuthEventListener {

    private final RoleAssignmentRepository assignments;
    private final String defaultRole;

    public DefaultRoleAssigner(RoleAssignmentRepository assignments, String defaultRole) {
        this.assignments = assignments;
        this.defaultRole = defaultRole;
    }

    @Override
    public void onEvent(AuthEvent event) {
        if (event instanceof AuthEvent.UserRegistered registered) {
            assignments.assign(registered.username(), defaultRole);
        }
    }
}