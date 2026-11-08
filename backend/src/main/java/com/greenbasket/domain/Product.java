package com.greenbasket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Entity
@Table(name = "products")
public class Product {

    public enum Status { EXPIRED, OUT, LOW, EXPIRING, OK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "name_key", nullable = false, length = 80)
    private String nameKey;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cost = BigDecimal.ZERO;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel = 10;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected Product() { }

    public Product(String name, Category category, Supplier supplier, BigDecimal price, BigDecimal cost,
                   int quantity, int reorderLevel, LocalDate expiryDate) {
        setName(name);
        this.category = category;
        this.supplier = supplier;
        this.price = price;
        this.cost = cost;
        this.quantity = quantity;
        this.reorderLevel = reorderLevel;
        this.expiryDate = expiryDate;
    }

    public static String keyOf(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }

    // ---------------------------------------------------------------- derived state
    public boolean isExpired() { return expiryDate != null && expiryDate.isBefore(LocalDate.now()); }

    public boolean isOutOfStock() { return quantity <= 0; }

    public boolean isLowStock() { return quantity > 0 && quantity <= reorderLevel; }

    public boolean isExpiringWithin(int days) {
        return expiryDate != null && !isExpired() && !expiryDate.isAfter(LocalDate.now().plusDays(days));
    }

    public Integer daysToExpiry() {
        return expiryDate == null ? null : (int) ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    public Status statusFor(int expiryWarnDays) {
        if (isExpired()) return Status.EXPIRED;
        if (isOutOfStock()) return Status.OUT;
        if (isLowStock()) return Status.LOW;
        if (isExpiringWithin(expiryWarnDays)) return Status.EXPIRING;
        return Status.OK;
    }

    public BigDecimal marginPercent() {
        if (price == null || price.signum() <= 0) return BigDecimal.ZERO;
        return price.subtract(cost).multiply(BigDecimal.valueOf(100)).divide(price, 1, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v.trim(); this.nameKey = keyOf(v); }
    public String getNameKey() { return nameKey; }
    public Category getCategory() { return category; }
    public void setCategory(Category v) { this.category = v; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier v) { this.supplier = v; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal v) { this.price = v; }
    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal v) { this.cost = v; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int v) { this.quantity = v; }
    public int getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(int v) { this.reorderLevel = v; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate v) { this.expiryDate = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = LocalDateTime.now(); }
}
