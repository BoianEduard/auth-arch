package com.saps.auth.layered;

import com.saps.auth.api.AuthException;
import com.saps.auth.api.AuthService;
import com.saps.auth.api.AuthorizationService;
import com.saps.auth.api.Identity;
import com.saps.auth.api.LoginRequest;
import com.saps.auth.api.LoginResult;
import com.saps.auth.api.Permission;
import com.saps.auth.api.RegisterRequest;
import com.saps.auth.api.RequestContext;
import com.saps.auth.layered.data.InMemoryUserRepository;
import com.saps.auth.layered.service.LayeredAuthService;
import com.saps.auth.support.events.SimpleEventBus;
import com.saps.auth.support.hashing.Sha256SaltedHasher;
import com.saps.auth.support.rbac.DefaultRoleAssigner;
import com.saps.auth.support.rbac.InMemoryRoleAssignmentRepository;
import com.saps.auth.support.rbac.InMemoryRoleRepository;
import com.saps.auth.support.rbac.RbacAuthorizationService;
import com.saps.auth.support.rbac.Role;
import com.saps.auth.support.rbac.RoleAssignmentRepository;
import com.saps.auth.support.rbac.RoleRepository;
import com.saps.auth.support.token.HmacTokenService;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;

public class Demo {

    public static void main(String[] args) {
        Clock clock = Clock.systemUTC();

        // ---- authorization wiring ----
        RoleRepository roles = new InMemoryRoleRepository();
        RoleAssignmentRepository assignments = new InMemoryRoleAssignmentRepository();

        Permission profileRead = Permission.of("profile:read");
        Permission usersManage = Permission.of("users:manage");
        roles.save(new Role("USER", Set.of(profileRead)));
        roles.save(new Role("ADMIN", Set.of(profileRead, usersManage)));

        AuthorizationService authz = new RbacAuthorizationService(roles, assignments);

        // ---- authentication wiring ----
        SimpleEventBus bus = new SimpleEventBus();
        bus.subscribe(new DefaultRoleAssigner(assignments, "USER")); // Observer: default role
        bus.subscribe(event -> System.out.println("[event] " + event)); // Observer: console log

        AuthService auth = new LayeredAuthService(
                new InMemoryUserRepository(),
                new Sha256SaltedHasher(),
                new HmacTokenService(
                        "dev-secret-change-me".getBytes(StandardCharsets.UTF_8),
                        Duration.ofMinutes(15),
                        clock),
                bus,
                clock);

        RequestContext ctx = new RequestContext("127.0.0.1");

        // ---- alice: promoted to ADMIN by hand ----
        auth.register(new RegisterRequest("alice", "correct-horse"));
        assignments.assign("alice", "ADMIN");

        // ---- bob: plain USER via the Observer ----
        auth.register(new RegisterRequest("bob", "battery-staple"));

        for (String name : new String[]{"alice", "bob"}) {
            String password = name.equals("alice") ? "correct-horse" : "battery-staple";
            LoginResult result = auth.login(new LoginRequest(name, password, ctx));

            if (result instanceof LoginResult.Authenticated authenticated) {
                Identity who = auth.verify(authenticated.token().value());
                System.out.println(name + " profile:read -> " + authz.isAllowed(who, profileRead));
                System.out.println(name + " users:manage -> " + authz.isAllowed(who, usersManage));
            }
        }

        // ---- failure path ----
        try {
            auth.login(new LoginRequest("alice", "wrong-password", ctx));
        } catch (AuthException e) {
            System.out.println("failed = " + e.failure());
        }
    }
}