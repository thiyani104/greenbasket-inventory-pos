package model;

import java.time.LocalDateTime;
import util.Fmt;
import util.PasswordUtil;

/** An account. Passwords are stored only as a salted PBKDF2 hash. */
public abstract class User {

    public static final String[] CSV_HEADER =
            {"id", "username", "fullName", "role", "salt", "hash", "active", "lastLogin", "created"};

    protected final int userId;
    protected final String username;
    protected String fullName;
    protected final Role role;
    protected String salt;
    protected String hash;
    protected boolean active;
    protected LocalDateTime lastLogin;
    protected final LocalDateTime created;

    protected User(int userId, String username, String fullName, Role role, String salt, String hash,
                   boolean active, LocalDateTime lastLogin, LocalDateTime created) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.salt = salt;
        this.hash = hash;
        this.active = active;
        this.lastLogin = lastLogin;
        this.created = created == null ? LocalDateTime.now() : created;
    }

    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public void setFullName(String n) { this.fullName = n; }
    public Role getRole() { return role; }
    public boolean isManager() { return role == Role.MANAGER; }
    public boolean isActive() { return active; }
    public void setActive(boolean a) { this.active = a; }
    public LocalDateTime getLastLogin() { return lastLogin; }
    public LocalDateTime getCreated() { return created; }
    public void markLogin() { this.lastLogin = LocalDateTime.now(); }

    public boolean checkPassword(String password) {
        return password != null && PasswordUtil.matches(password, salt, hash);
    }

    public void setPassword(String password) {
        this.salt = PasswordUtil.newSalt();
        this.hash = PasswordUtil.hash(password, salt);
    }

    /** True when the name matches (case-insensitively), the account is active and the password is right. */
    public boolean login(String name, String password) {
        return active && username.equalsIgnoreCase(name) && checkPassword(password);
    }

    /** A copy of this account with a different role (same credentials, history and status). */
    public User withRole(Role r) {
        return r == Role.MANAGER
                ? new StoreManager(userId, username, fullName, salt, hash, active, lastLogin, created)
                : new SalesAssistant(userId, username, fullName, salt, hash, active, lastLogin, created);
    }

    protected static String[] hashed(String password) {
        String s = PasswordUtil.newSalt();
        return new String[]{s, PasswordUtil.hash(password, s)};
    }

    public String[] toCsv() {
        return new String[]{String.valueOf(userId), username, fullName, role.name(), salt, hash,
                String.valueOf(active), lastLogin == null ? "" : Fmt.dateTime(lastLogin), Fmt.dateTime(created)};
    }

    public static User fromCsv(String[] f) {
        int id = Integer.parseInt(f[0].trim());
        Role r = Role.valueOf(f[3].trim());
        boolean active = f.length < 7 || f[6].isBlank() || Boolean.parseBoolean(f[6].trim());
        LocalDateTime last = f.length > 7 && !f[7].isBlank() ? Fmt.parseDateTime(f[7]) : null;
        LocalDateTime created = f.length > 8 && !f[8].isBlank() ? Fmt.parseDateTime(f[8]) : null;
        return r == Role.MANAGER
                ? new StoreManager(id, f[1], f[2], f[4], f[5], active, last, created)
                : new SalesAssistant(id, f[1], f[2], f[4], f[5], active, last, created);
    }

    @Override public String toString() { return fullName + " (" + username + ")"; }
}
