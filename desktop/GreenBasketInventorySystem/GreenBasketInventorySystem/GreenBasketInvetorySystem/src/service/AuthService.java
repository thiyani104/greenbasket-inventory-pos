package service;

import java.util.*;
import model.*;
import util.FileManager;
import util.PasswordUtil;

/** Accounts, login with lockout, and password management. */
public class AuthService {

    public static class AuthException extends Exception {
        public AuthException(String message) { super(message); }
    }

    private static final String FILE = "users.csv";
    private static final int MAX_ATTEMPTS = 3;
    private static final long LOCK_MILLIS = 30_000;
    public static final String DEFAULT_ADMIN = "admin";
    public static final String DEFAULT_PASSWORD = "1234";

    private final FileManager fm;
    private final ActivityLog log;
    private final List<User> users = new ArrayList<>();
    private final Map<String, Integer> failures = new HashMap<>();
    private final Map<String, Long> lockedUntil = new HashMap<>();

    public AuthService(FileManager fm, ActivityLog log) {
        this.fm = fm;
        this.log = log;
        for (String[] r : fm.readCsv(FILE)) {
            try {
                users.add(User.fromCsv(r));
            } catch (RuntimeException ignored) { }
        }
        if (users.isEmpty()) {
            users.add(StoreManager.create(1, DEFAULT_ADMIN, "Administrator", DEFAULT_PASSWORD));
            save();
        }
    }

    private void save() {
        List<String[]> rows = new ArrayList<>();
        for (User u : users) rows.add(u.toCsv());
        fm.writeCsv(FILE, User.CSV_HEADER, rows);
    }

    public List<User> getUsers() { return Collections.unmodifiableList(users); }

    public User findByUsername(String username) {
        for (User u : users) if (u.getUsername().equalsIgnoreCase(username)) return u;
        return null;
    }

    public User authenticate(String username, String password) throws AuthException {
        String key = username == null ? "" : username.trim().toLowerCase();
        if (key.isEmpty() || password == null || password.isEmpty())
            throw new AuthException("Enter your username and password.");
        Long until = lockedUntil.get(key);
        if (until != null) {
            long left = until - System.currentTimeMillis();
            if (left > 0) throw new AuthException("Too many attempts. Try again in " + (left / 1000 + 1) + "s.");
            lockedUntil.remove(key);
            failures.remove(key);
        }
        User u = findByUsername(key);
        if (u != null && !u.isActive()) {
            throw new AuthException("This account has been deactivated. Contact the manager.");
        }
        if (u != null && u.login(key, password)) {
            failures.remove(key);
            u.markLogin();
            save();
            log.log(u.getUsername(), "LOGIN", "Signed in");
            return u;
        }
        int n = failures.merge(key, 1, Integer::sum);
        log.log(key, "LOGIN_FAILED", "Attempt " + n);
        if (n >= MAX_ATTEMPTS) {
            lockedUntil.put(key, System.currentTimeMillis() + LOCK_MILLIS);
            throw new AuthException("Too many failed attempts. Locked for 30 seconds.");
        }
        throw new AuthException("Incorrect username or password (" + (MAX_ATTEMPTS - n) + " attempts left).");
    }

    /** True while the built-in admin still uses the factory password. */
    public boolean isUsingDefaultPassword(User u) {
        return u != null && u.getUsername().equalsIgnoreCase(DEFAULT_ADMIN) && u.checkPassword(DEFAULT_PASSWORD);
    }

    public User createUser(Role role, String username, String fullName, String password, String actor) {
        username = username == null ? "" : username.trim();
        if (username.length() < 3) throw new IllegalArgumentException("Username must be at least 3 characters.");
        if (!username.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("Username may only contain letters, numbers, dot, dash and underscore.");
        if (findByUsername(username) != null) throw new IllegalArgumentException("Username \"" + username + "\" is already taken.");
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("Full name is required.");
        String problem = PasswordUtil.validateStrength(password);
        if (problem != null) throw new IllegalArgumentException(problem);
        int id = users.stream().mapToInt(User::getUserId).max().orElse(0) + 1;
        User u = role == Role.MANAGER ? StoreManager.create(id, username, fullName.trim(), password)
                : SalesAssistant.create(id, username, fullName.trim(), password);
        users.add(u);
        save();
        log.log(actor, "USER_CREATED", username + " (" + role.label() + ")");
        return u;
    }

    public void changePassword(User u, String oldPassword, String newPassword) {
        if (!u.checkPassword(oldPassword)) throw new IllegalArgumentException("Current password is incorrect.");
        String problem = PasswordUtil.validateStrength(newPassword);
        if (problem != null) throw new IllegalArgumentException(problem);
        u.setPassword(newPassword);
        save();
        log.log(u.getUsername(), "PASSWORD_CHANGED", "");
    }

    public void resetPassword(User target, String newPassword, String actor) {
        String problem = PasswordUtil.validateStrength(newPassword);
        if (problem != null) throw new IllegalArgumentException(problem);
        target.setPassword(newPassword);
        save();
        log.log(actor, "PASSWORD_RESET", target.getUsername());
    }

    public void setActive(User target, boolean active, String actor) {
        if (!active && target.isManager() && activeManagers() <= 1)
            throw new IllegalArgumentException("You cannot deactivate the last active manager.");
        if (!active && target.getUsername().equalsIgnoreCase(actor))
            throw new IllegalArgumentException("You cannot deactivate your own account.");
        target.setActive(active);
        save();
        log.log(actor, active ? "USER_ACTIVATED" : "USER_DEACTIVATED", target.getUsername());
    }

    public void deleteUser(User target, String actor) {
        if (target.getUsername().equalsIgnoreCase(actor)) throw new IllegalArgumentException("You cannot delete your own account.");
        if (target.isManager() && activeManagers() <= 1 && target.isActive())
            throw new IllegalArgumentException("You cannot delete the last active manager.");
        users.remove(target);
        save();
        log.log(actor, "USER_DELETED", target.getUsername());
    }

    private long activeManagers() {
        return users.stream().filter(u -> u.isManager() && u.isActive()).count();
    }

    public void rename(User target, String fullName, String actor) {
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("Full name is required.");
        target.setFullName(fullName.trim());
        save();
        log.log(actor, "USER_RENAMED", target.getUsername() + " -> " + fullName.trim());
    }

    /** Switches an account between manager and sales assistant. Returns the replacement user object. */
    public User changeRole(User target, Role role, String actor) {
        if (target.getRole() == role) throw new IllegalArgumentException(target.getFullName() + " is already a " + role.label() + ".");
        if (target.getUsername().equalsIgnoreCase(actor)) throw new IllegalArgumentException("You cannot change your own role.");
        if (target.isManager() && target.isActive() && activeManagers() <= 1)
            throw new IllegalArgumentException("You cannot demote the last active manager.");
        int i = users.indexOf(target);
        if (i < 0) throw new IllegalArgumentException("That user no longer exists.");
        User replacement = target.withRole(role);
        users.set(i, replacement);
        save();
        log.log(actor, "USER_ROLE_CHANGED", target.getUsername() + " -> " + role.label());
        return replacement;
    }

    /** Legacy API: create a sales assistant. */
    public SalesAssistant createSalesAssistantAccount(String username, String fullName, String password, String actor) {
        return (SalesAssistant) createUser(Role.ASSISTANT, username, fullName, password, actor);
    }
}
