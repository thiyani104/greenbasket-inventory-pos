package model;

import java.time.LocalDateTime;
import util.PasswordUtil;

/** A staff account. Permissions come from the role; passwords are only ever stored hashed. */
public abstract class User {

    protected final int userId;
    protected final String username;
    protected String fullName;
    protected String passwordHash;
    protected boolean active = true;
    protected LocalDateTime createdAt = LocalDateTime.now().withNano(0);
    protected LocalDateTime lastLogin;

    protected User(int userId, String username, String fullName, String passwordHash) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName == null || fullName.isBlank() ? username : fullName;
        this.passwordHash = passwordHash;
    }

    public abstract Role getRole();

    // ---- permissions (one place, so the UI and the services agree) ----
    public boolean canEditProducts() { return false; }
    public boolean canViewReports() { return false; }
    public boolean canVoidSales() { return false; }
    public boolean canManageUsers() { return false; }
    public boolean canViewCosts() { return false; }
    public boolean canAdjustStock() { return false; }
    public boolean canChangeSettings() { return false; }
    public double getMaxDiscountPercent() { return 10; }

    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public void setFullName(String v) { this.fullName = v; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { if (v != null) this.createdAt = v; }
    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime v) { this.lastLogin = v; }

    public boolean checkPassword(String password) { return PasswordUtil.verify(password, passwordHash); }

    public void setPassword(String password) { this.passwordHash = PasswordUtil.hash(password); }

    public String getInitials() {
        String[] parts = fullName.trim().split("\\s+");
        String s = parts[0].isEmpty() ? "?" : parts[0].substring(0, 1);
        if (parts.length > 1) s += parts[parts.length - 1].substring(0, 1);
        return s.toUpperCase();
    }

    @Override public String toString() { return fullName + " (" + username + ")"; }
}
