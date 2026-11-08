package service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import model.Role;
import model.SalesAssistant;
import model.StoreManager;
import model.User;
import util.FileManager;
import util.PasswordUtil;

public class UserService {

    public static final String DEFAULT_ADMIN = "admin";
    public static final String DEFAULT_PASSWORD = "1234";
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MILLIS = 30_000;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,20}");

    private static class Attempts {
        int failures;
        long lockedUntil;
    }

    private final FileManager files;
    private final ActivityLog log;
    private final List<User> users = new ArrayList<>();
    private final Map<String, Attempts> attempts = new HashMap<>();

    public UserService(FileManager files, ActivityLog log) {
        this.files = files;
        this.log = log;
    }

    /** Loads users; creates the default manager account when there are none. */
    public void load() {
        users.clear();
        users.addAll(files.loadUsers());
        if (users.isEmpty()) {
            users.add(new StoreManager(1, DEFAULT_ADMIN, "Store Manager", PasswordUtil.hash(DEFAULT_PASSWORD)));
            files.saveUsers(users);
        }
    }

    public List<User> getAll() { return Collections.unmodifiableList(users); }

    public User findById(int id) {
        for (User u : users) if (u.getUserId() == id) return u;
        return null;
    }

    public User findByUsername(String username) {
        for (User u : users) if (u.getUsername().equalsIgnoreCase(username)) return u;
        return null;
    }

    // ============================== authentication ==============================

    public User authenticate(String username, String password) {
        String key = username == null ? "" : username.trim().toLowerCase();
        if (key.isEmpty() || password == null || password.isEmpty()) {
            throw new ServiceException("Enter your username and password.");
        }
        Attempts a = attempts.computeIfAbsent(key, k -> new Attempts());
        long now = System.currentTimeMillis();
        if (a.lockedUntil > now) {
            long secs = (a.lockedUntil - now + 999) / 1000;
            throw new ServiceException("Too many failed attempts. Try again in " + secs + " seconds.");
        }
        User u = findByUsername(key);
        if (u != null && u.checkPassword(password)) {
            if (!u.isActive()) throw new ServiceException("This account has been deactivated. Contact the store manager.");
            attempts.remove(key);
            u.setLastLogin(LocalDateTime.now().withNano(0));
            files.saveUsers(users);
            log.add(u.getUsername(), "Login", "Signed in");
            return u;
        }
        a.failures++;
        log.add(key, "Failed login", "Attempt " + a.failures);
        if (a.failures >= MAX_ATTEMPTS) {
            a.failures = 0;
            a.lockedUntil = now + LOCK_MILLIS;
            throw new ServiceException("Too many failed attempts. Try again in " + (LOCK_MILLIS / 1000) + " seconds.");
        }
        throw new ServiceException("Invalid username or password.");
    }

    public boolean isUsingDefaultPassword(User u) {
        return u.getUsername().equalsIgnoreCase(DEFAULT_ADMIN) && u.checkPassword(DEFAULT_PASSWORD);
    }

    // ============================== management ==============================

    public static void validatePassword(String pw) {
        if (pw == null || pw.length() < 6) throw new ServiceException("Password must be at least 6 characters.");
        if (pw.length() > 64) throw new ServiceException("Password is too long.");
    }

    private int nextId() {
        int max = 0;
        for (User u : users) max = Math.max(max, u.getUserId());
        return max + 1;
    }

    public User addUser(String username, String fullName, Role role, String password, User actor) {
        if (!actor.canManageUsers()) throw new ServiceException("Only a store manager can create accounts.");
        username = username == null ? "" : username.trim();
        if (!USERNAME.matcher(username).matches()) {
            throw new ServiceException("Username must be 3-20 characters: letters, numbers, dot, dash or underscore.");
        }
        if (findByUsername(username) != null) throw new ServiceException("That username is already taken.");
        if (fullName == null || fullName.isBlank()) throw new ServiceException("Full name is required.");
        validatePassword(password);
        String hash = PasswordUtil.hash(password);
        User u = role == Role.MANAGER
                ? new StoreManager(nextId(), username, fullName.trim(), hash)
                : new SalesAssistant(nextId(), username, fullName.trim(), hash);
        users.add(u);
        files.saveUsers(users);
        log.add(actor.getUsername(), "Create user", username + " (" + role.getLabel() + ")");
        return u;
    }

    private int activeManagers() {
        int n = 0;
        for (User u : users) if (u.isActive() && u.getRole() == Role.MANAGER) n++;
        return n;
    }

    /** Changes name / role / active flag. Role changes replace the account object. */
    public User updateUser(int id, String fullName, Role role, boolean active, User actor) {
        if (!actor.canManageUsers()) throw new ServiceException("Only a store manager can edit accounts.");
        User u = findById(id);
        if (u == null) throw new ServiceException("User not found.");
        if (fullName == null || fullName.isBlank()) throw new ServiceException("Full name is required.");
        if (u == actor && !active) throw new ServiceException("You cannot deactivate your own account.");
        boolean losesManager = u.getRole() == Role.MANAGER && u.isActive() && (role != Role.MANAGER || !active);
        if (losesManager && activeManagers() <= 1) {
            throw new ServiceException("There must always be at least one active store manager.");
        }
        if (u == actor && u.getRole() != role) throw new ServiceException("You cannot change your own role.");
        if (u.getRole() != role) {
            User replacement = role == Role.MANAGER
                    ? new StoreManager(u.getUserId(), u.getUsername(), fullName.trim(), u.getPasswordHash())
                    : new SalesAssistant(u.getUserId(), u.getUsername(), fullName.trim(), u.getPasswordHash());
            replacement.setCreatedAt(u.getCreatedAt());
            replacement.setLastLogin(u.getLastLogin());
            users.set(users.indexOf(u), replacement);
            u = replacement;
        }
        u.setFullName(fullName.trim());
        u.setActive(active);
        files.saveUsers(users);
        log.add(actor.getUsername(), "Update user", u.getUsername() + " · " + role.getLabel() + (active ? "" : " · deactivated"));
        return u;
    }

    public void resetPassword(int id, String newPassword, User actor) {
        if (!actor.canManageUsers()) throw new ServiceException("Only a store manager can reset passwords.");
        User u = findById(id);
        if (u == null) throw new ServiceException("User not found.");
        validatePassword(newPassword);
        u.setPassword(newPassword);
        files.saveUsers(users);
        log.add(actor.getUsername(), "Reset password", u.getUsername());
    }

    public void changeOwnPassword(User user, String current, String newPassword) {
        if (!user.checkPassword(current)) throw new ServiceException("Your current password is incorrect.");
        validatePassword(newPassword);
        if (current.equals(newPassword)) throw new ServiceException("Choose a password different from the current one.");
        user.setPassword(newPassword);
        files.saveUsers(users);
        log.add(user.getUsername(), "Change password", "Password updated");
    }
}
