package model;

import java.time.LocalDate;

/** A catalogue item. Prices are in the store currency; stock is a whole number of units. */
public class Product {

    private int productId;
    private String productName;
    private String category;
    private String supplier;
    private double price;
    private double cost;
    private int quantity;
    private int reorderLevel;
    private LocalDate expiryDate;

    public Product(int productId, String productName, String category, String supplier,
                   double price, double cost, int quantity, int reorderLevel, LocalDate expiryDate) {
        this.productId = productId;
        this.productName = productName == null ? "" : productName.trim();
        this.category = category == null ? "" : category.trim();
        this.supplier = supplier == null ? "" : supplier.trim();
        this.price = price;
        this.cost = cost;
        this.quantity = quantity;
        this.reorderLevel = reorderLevel;
        this.expiryDate = expiryDate;
    }

    public Product copy() {
        return new Product(productId, productName, category, supplier, price, cost, quantity, reorderLevel, expiryDate);
    }

    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public String getSupplier() { return supplier; }
    public double getPrice() { return price; }
    public double getCost() { return cost; }
    public int getQuantity() { return quantity; }
    public int getReorderLevel() { return reorderLevel; }
    public LocalDate getExpiryDate() { return expiryDate; }

    public void setProductId(int id) { this.productId = id; }
    public void setProductName(String v) { this.productName = v == null ? "" : v.trim(); }
    public void setCategory(String v) { this.category = v == null ? "" : v.trim(); }
    public void setSupplier(String v) { this.supplier = v == null ? "" : v.trim(); }
    public void setPrice(double v) { this.price = v; }
    public void setCost(double v) { this.cost = v; }
    public void setQuantity(int v) { this.quantity = v; }
    public void setReorderLevel(int v) { this.reorderLevel = v; }
    public void setExpiryDate(LocalDate v) { this.expiryDate = v; }

    // ---------------------------------------------------------------- derived state

    public boolean isOutOfStock() { return quantity <= 0; }

    /** In stock, but at or below the reorder level. */
    public boolean isLowStock() { return quantity > 0 && quantity <= reorderLevel; }

    /** Out of stock or low: something the manager should reorder. */
    public boolean needsRestock() { return quantity <= reorderLevel; }

    public boolean isExpired() { return expiryDate != null && expiryDate.isBefore(LocalDate.now()); }

    public boolean isExpiringWithin(int days) {
        if (expiryDate == null || isExpired()) return false;
        return !expiryDate.isAfter(LocalDate.now().plusDays(days));
    }

    /** Days until expiry (negative when already expired); null when the item does not expire. */
    public Integer daysToExpiry() {
        return expiryDate == null ? null : (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    public StockStatus getStatus(int expiryWarnDays) {
        if (isExpired()) return StockStatus.EXPIRED;
        if (isOutOfStock()) return StockStatus.OUT;
        if (isLowStock()) return StockStatus.LOW;
        if (isExpiringWithin(expiryWarnDays)) return StockStatus.EXPIRING;
        return StockStatus.OK;
    }

    public double marginPercent() { return price <= 0 ? 0 : (price - cost) / price * 100.0; }

    public double stockValueAtCost() { return Math.max(0, quantity) * cost; }

    public double stockValueAtRetail() { return Math.max(0, quantity) * price; }

    @Override public String toString() { return productName; }
}
