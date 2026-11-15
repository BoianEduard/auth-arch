package com.saps.auth.layered;

import com.saps.auth.api.AuthException;
import com.saps.auth.api.AuthFailure;
import com.saps.auth.api.AuthSession;
import com.saps.auth.api.MfaCapableAuthService;
import com.saps.auth.api.AuthorizationService;
import com.saps.auth.api.Identity;
import com.saps.auth.api.LoginRequest;
import com.saps.auth.api.LoginResult;
import com.saps.auth.api.MfaVerifyRequest;
import com.saps.auth.api.Permission;
import com.saps.auth.api.RegisterRequest;
import com.saps.auth.api.RequestContext;
import com.saps.auth.layered.data.InMemoryUserRepository;
import com.saps.auth.layered.service.LayeredAuthService;
import com.saps.auth.support.events.AuthEventPublisher;
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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

public class Demo {

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Choose a demo:");
            System.out.println("1. Run the initial demo");
            System.out.println("2. Run an interactive authentication demo");
            prompt("> ");

            if (!scanner.hasNextLine()) {
                System.out.println("No selection provided.");
                return;
            }

            switch (scanner.nextLine().trim()) {
                case "1" -> runInitialDemo(scanner);
                case "2" -> runInteractiveDemo(scanner);
                default -> System.out.println("Invalid selection. Choose 1 or 2.");
            }
        }
    }

    private static void runInitialDemo(Scanner scanner) {
        Path logPath = Path.of("auth-events.log");
        try (BufferedWriter eventLog = openEventLog(logPath)) {
            runInitialDemo(scanner, eventLog, logPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to open authentication event log at "
                    + logPath.toAbsolutePath(), e);
        }
    }

    private static void runInitialDemo(
            Scanner scanner, BufferedWriter eventLog, Path logPath) {
        Clock clock = Clock.systemUTC();

        // ---- authorization wiring ----
        RoleRepository roles = new InMemoryRoleRepository();
        RoleAssignmentRepository assignments = new InMemoryRoleAssignmentRepository();

        Permission profileRead = Permission.of("profile:read");
        Permission usersManage = Permission.of("users:manage");
        roles.save(new Role("USER", Set.of(profileRead)));
        roles.save(new Role("ADMIN", Set.of(profileRead, usersManage)));

        AuthorizationService authz = new RbacAuthorizationService(roles, assignments);

        Map<String, String> demoMfaCodes = new HashMap<>();
        MfaCapableAuthService auth = createAuthService(
                clock,
                assignments,
                eventLog,
                logPath,
                demoMfaCodes::put);

        RequestContext ctx = new RequestContext("127.0.0.1");

        // ---- alice: promoted to ADMIN by hand ----
        auth.register(new RegisterRequest("alice", "correct-horse"));
        printMfaCode(demoMfaCodes.get("alice"));
        assignments.assign("alice", "ADMIN");

        // ---- bob: plain USER via the Observer ----
        auth.register(new RegisterRequest("bob", "battery-staple"));
        printMfaCode(demoMfaCodes.get("bob"));

        for (String name : new String[]{"alice", "bob"}) {
            String password = name.equals("alice") ? "correct-horse" : "battery-staple";
            String issuedCode = demoMfaCodes.get(name);
            LoginResult result = auth.login(new LoginRequest(name, password, ctx));
            String currentCode = demoMfaCodes.get(name);
            if (!java.util.Objects.equals(issuedCode, currentCode)) {
                printMfaCode(currentCode);
            }

            if (result instanceof LoginResult.Authenticated authenticated) {
                Identity who = auth.verify(authenticated.session().accessToken().value());
                printGreen(name + " profile:read -> " + authz.isAllowed(who, profileRead));
                printGreen(name + " users:manage -> " + authz.isAllowed(who, usersManage));
            } else if (result instanceof LoginResult.MfaRequired mfaRequired) {
                AuthSession session = auth.completeMfa(
                        new MfaVerifyRequest(
                                mfaRequired.challengeToken(), demoMfaCodes.remove(name)));
                Identity who = auth.verify(session.accessToken().value());
                printGreen(name + " profile:read -> " + authz.isAllowed(who, profileRead));
                printGreen(name + " users:manage -> " + authz.isAllowed(who, usersManage));
                AuthSession refreshed = auth.refresh(session.refreshToken());
                expectFailure(AuthFailure.INVALID_TOKEN,
                        () -> auth.verify(session.accessToken().value()));
                expectFailure(AuthFailure.INVALID_REFRESH_TOKEN,
                        () -> auth.refresh(session.refreshToken()));
                if (readYesNo(scanner, "Log out " + name + "? (y/n): ")) {
                    auth.logout(refreshed);
                    expectFailure(AuthFailure.INVALID_TOKEN,
                            () -> auth.verify(refreshed.accessToken().value()));
                    expectFailure(AuthFailure.INVALID_REFRESH_TOKEN,
                            () -> auth.refresh(refreshed.refreshToken()));
                    System.out.println(name + " logged out.");
                } else {
                    auth.verify(refreshed.accessToken().value());
                    System.out.println(name + " remains logged in.");
                }
            }
        }

        // ---- failure path ----
        try {
            auth.login(new LoginRequest("alice", "wrong-password", ctx));
        } catch (AuthException e) {
            System.out.println("failed = " + e.failure());
        }
    }

    private static void runInteractiveDemo(Scanner scanner) {
        Path logPath = Path.of("auth-events.log");
        try (BufferedWriter eventLog = openEventLog(logPath)) {
            runInteractiveDemo(scanner, eventLog, logPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to open authentication event log at "
                    + logPath.toAbsolutePath(), e);
        }
    }

    private static void runInteractiveDemo(
            Scanner scanner, BufferedWriter eventLog, Path logPath) {
        if (!readYesNo(scanner, "Do you want to register a user? (y/n): ")) {
            System.out.println("Registration declined. No session exists to revoke.");
            return;
        }

        Clock clock = Clock.systemUTC();
        RoleAssignmentRepository assignments = new InMemoryRoleAssignmentRepository();
        RoleRepository roles = new InMemoryRoleRepository();
        Permission profileRead = Permission.of("profile:read");
        Permission usersManage = Permission.of("users:manage");
        roles.save(new Role("USER", Set.of(profileRead)));
        roles.save(new Role("ADMIN", Set.of(profileRead, usersManage)));
        AuthorizationService authz = new RbacAuthorizationService(roles, assignments);
        AtomicReference<String> pendingMfaCode = new AtomicReference<>();
        MfaCapableAuthService auth = createAuthService(
                clock,
                assignments,
                eventLog,
                logPath,
                (user, code) -> pendingMfaCode.set(code));

        String username = registerUser(scanner, auth);
        boolean isAdmin = readYesNo(
                scanner, "Should this account have admin rights? (y/n): ");

        if (isAdmin) {
            assignments.assign(username, "ADMIN");
        }
        System.out.println("Account registered successfully.");
        printMfaCode(pendingMfaCode);

        if (!readYesNo(scanner, "Do you want to log in? (y/n): ")) {
            System.out.println("Registration complete. No login was started.");
            return;
        }

        prompt("  Login username: ");
        String loginUsername = scanner.nextLine().trim();
        prompt("  Login password: ");
        String loginPassword = scanner.nextLine();

        String previouslyIssuedCode = pendingMfaCode.get();
        LoginResult result;
        try {
            result = auth.login(new LoginRequest(
                    loginUsername, loginPassword, RequestContext.unknown()));
        } catch (AuthException exception) {
            if (exception.failure() == AuthFailure.INVALID_CREDENTIALS) {
                System.out.println("Login failed: invalid username or password.");
                return;
            }
            if (exception.failure() == AuthFailure.INVALID_INPUT) {
                System.out.println("Login failed: username and password are required.");
                return;
            }
            throw exception;
        }
        if (!java.util.Objects.equals(previouslyIssuedCode, pendingMfaCode.get())) {
            printMfaCode(pendingMfaCode);
        }

        AuthSession session;
        if (result instanceof LoginResult.MfaRequired challenge) {
            session = completeMfaWithResend(scanner, auth, challenge, pendingMfaCode);
            if (session == null) {
                System.out.println("Login ended; no session was created.");
                return;
            }
        } else if (result instanceof LoginResult.Authenticated authenticated) {
            session = authenticated.session();
        } else {
            throw new IllegalStateException("Unsupported login result: " + result);
        }

        Identity identity = auth.verify(session.accessToken().value());
        printGreen("Authenticated as " + identity.username());
        printGreen("profile:read -> " + authz.isAllowed(identity, profileRead));
        printGreen("users:manage -> " + authz.isAllowed(identity, usersManage));

        AuthSession activeSession = auth.refresh(session.refreshToken());
        System.out.println("Refresh token rotated successfully.");
        if (readYesNo(scanner, "Log out now? (y/n): ")) {
            auth.logout(activeSession);
            System.out.println("Logged out; the session has been revoked.");
        } else {
            System.out.println("You chose to stay logged in. The session was not logged out.");
        }
    }

    private static String registerUser(Scanner scanner, MfaCapableAuthService auth) {
        while (true) {
            prompt("  Username: ");
            String username = scanner.nextLine().trim();
            prompt("  Password (at least 8 characters): ");
            String password = scanner.nextLine();
            try {
                auth.register(new RegisterRequest(username, password));
                return username;
            } catch (AuthException exception) {
                if (exception.failure() == AuthFailure.INVALID_INPUT) {
                    System.out.println("Registration failed: a username is required"
                            + " and the password needs at least 8 characters.");
                } else if (exception.failure() == AuthFailure.USER_ALREADY_EXISTS) {
                    System.out.println("Registration failed: this username is already taken.");
                } else {
                    throw exception;
                }
            }
        }
    }

    private static AuthSession completeMfaWithResend(
            Scanner scanner,
            MfaCapableAuthService auth,
            LoginResult.MfaRequired initialChallenge,
            AtomicReference<String> pendingMfaCode) {
        LoginResult.MfaRequired challenge = initialChallenge;
        while (true) {
            prompt("  Enter the 6-digit MFA code or type 'resend': ");
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("resend")) {
                challenge = auth.resendMfa(challenge.challengeToken());
                printMfaCode(pendingMfaCode);
                continue;
            }
            if (input.isEmpty()) {
                System.out.println("No code entered. Type the 6-digit code or 'resend'.");
                continue;
            }

            try {
                return auth.completeMfa(
                        new MfaVerifyRequest(challenge.challengeToken(), input));
            } catch (AuthException exception) {
                if (exception.failure() == AuthFailure.MFA_CHALLENGE_EXPIRED) {
                    System.out.println("The MFA code expired.");
                    if (readYesNo(scanner, "Request a new MFA code? (y/n): ")) {
                        challenge = auth.resendMfa(challenge.challengeToken());
                        printMfaCode(pendingMfaCode);
                    } else {
                        return null;
                    }
                } else if (exception.failure() == AuthFailure.MFA_INVALID_CODE) {
                    System.out.println("Incorrect MFA code. Try again or request a new code.");
                } else {
                    throw exception;
                }
            }
        }
    }

    private static void printMfaCode(AtomicReference<String> pendingMfaCode) {
        printMfaCode(pendingMfaCode.get());
    }

    private static void printMfaCode(String code) {
        if (code != null) {
            printRed("  MFA code: " + code);
        }
    }

    private static void printRed(String text) {
        printColoredLine(text, "\u001B[31m");
    }

    private static void printGreen(String text) {
        printColoredLine(text, "\u001B[32m");
    }

    private static void printColoredLine(String text, String color) {
        if (System.console() != null) {
            System.out.println(color + text + "\u001B[0m");
        } else {
            System.out.println(text);
        }
    }

    private static boolean readYesNo(Scanner scanner, String prompt) {
        while (true) {
            boldQuestion(prompt);
            String answer = scanner.nextLine().trim();
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) {
                return true;
            }
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no")) {
                return false;
            }
            System.out.println("Please answer y or n.");
        }
    }

    private static void boldQuestion(String text) {
        if (System.console() != null) {
            System.out.print("\u001B[1m" + text + "\u001B[0m");
        } else {
            System.out.print(text);
        }
        System.out.flush();
    }

    private static void prompt(String text) {
        System.out.print(text);
        System.out.flush();
    }

    private static BufferedWriter openEventLog(Path logPath) throws IOException {
        return Files.newBufferedWriter(
                logPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    private static MfaCapableAuthService createAuthService(
            Clock clock,
            RoleAssignmentRepository assignments,
            BufferedWriter eventLog,
            Path logPath,
            BiConsumer<String, String> mfaCodeDelivery) {
        AuthEventPublisher events = withFileLogging(
                createEventPublisher(assignments), eventLog, logPath);
        return new LayeredAuthService(
                new InMemoryUserRepository(),
                new Sha256SaltedHasher(),
                new HmacTokenService(
                        "dev-secret-change-me".getBytes(StandardCharsets.UTF_8),
                        Duration.ofMinutes(15),
                        clock),
                events,
                mfaCodeDelivery,
                clock);
    }

    private static AuthEventPublisher createEventPublisher(
            RoleAssignmentRepository assignments) {
        SimpleEventBus bus = new SimpleEventBus();
        bus.subscribe(new DefaultRoleAssigner(assignments, "USER"));
        return bus;
    }

    private static AuthEventPublisher withFileLogging(
            AuthEventPublisher delegate, BufferedWriter eventLog, Path logPath) {
        return event -> {
            delegate.publish(event);
            try {
                eventLog.write(event.toString());
                eventLog.newLine();
                eventLog.flush();
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to write authentication event log at "
                        + logPath.toAbsolutePath(), e);
            }
        };
    }

    private static void expectFailure(AuthFailure expected, Runnable operation) {
        try {
            operation.run();
            throw new IllegalStateException("Expected authentication failure: " + expected);
        } catch (AuthException exception) {
            if (exception.failure() != expected) {
                throw exception;
            }
        }
    }
}