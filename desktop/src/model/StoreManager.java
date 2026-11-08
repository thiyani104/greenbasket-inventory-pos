package model;

public class StoreManager extends User {

    public StoreManager(int userId, String username, String fullName, String passwordHash) {
        super(userId, username, fullName, passwordHash);
    }

    @Override public Role getRole() { return Role.MANAGER; }
    @Override public boolean canEditProducts() { return true; }
    @Override public boolean canViewReports() { return true; }
    @Override public boolean canVoidSales() { return true; }
    @Override public boolean canManageUsers() { return true; }
    @Override public boolean canViewCosts() { return true; }
    @Override public boolean canAdjustStock() { return true; }
    @Override public boolean canChangeSettings() { return true; }
    @Override public double getMaxDiscountPercent() { return 100; }
}
