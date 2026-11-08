package model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import util.Fmt;

/** A catalogue item: pricing, stock level, reorder point, supplier, barcode and optional expiry date. */
public class Product {

    public static final String[] CSV_HEADER =
            {"id", "name", "category", "supplier", "cost", "price", "quantity", "reorder", "expiry", "barcode"};

    private final int productId;
    private String productName;
    private String category;
    private String supplier;
    private double costPrice;
    private double price;
    private int quantity;
    private int reorderLevel;
    private LocalDate expiryDate;
    private String barcode = "";

    public Product(int productId, String productName, String category, String supplier,
                   double costPrice, double price, int quantity, int reorderLevel, LocalDate expiryDate) {
        this.productId = productId;
        this.productName = productName == null ? "" : productName.trim();
        this.category = category == null ? "" : category.trim();
        this.supplier = supplier == null ? "" : supplier.trim();
        this.costPrice = costPrice;
        this.price = price;
        this.quantity = quantity;
        this.reorderLevel = reorderLevel;
        this.expiryDate = expiryDate;
    }

    // ------------------------------------------------------------ accessors

    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public String getSupplier() { return supplier; }
    public double getCostPrice() { return costPrice; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public int getReorderLevel() { return reorderLevel; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public String getBarcode() { return barcode; }

    public void setProductName(String v) { this.productName = v == null ? "" : v.trim(); }
    public void setCategory(String v) { this.category = v == null ? "" : v.trim(); }
    public void setSupplier(String v) { this.supplier = v == null ? "" : v.trim(); }
    public void setCostPrice(double v) { this.costPrice = v; }
    public void setPrice(double v) { this.price = v; }
    public void setQuantity(int v) { this.quantity = v; }
    public void setReorderLevel(int v) { this.reorderLevel = v; }
    public void setExpiryDate(LocalDate v) { this.expiryDate = v; }
    public void setBarcode(String v) { this.barcode = v == null ? "" : v.trim(); }

    // ------------------------------------------------------- stock & dating

    public boolean isOutOfStock() { return quantity <= 0; }

    /** Stock is at or below the reorder point but not yet sold out. */
    public boolean isLowStock() { return quantity > 0 && quantity <= reorderLevel; }

    /** Low or out: a purchasing decision is needed. */
    public boolean needsRestock() { return quantity <= reorderLevel; }

    public boolean hasExpiry() { return expiryDate != null; }

    public boolean isExpired() { return expiryDate != null && expiryDate.isBefore(LocalDate.now()); }

    public boolean isExpiringSoon(int withinDays) {
        if (expiryDate == null || isExpired()) return false;
        return daysToExpiry() <= withinDays;
    }

    /** Days until expiry (negative once expired); {@link Long#MAX_VALUE} for products that never expire. */
    public long daysToExpiry() {
        return expiryDate == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    /** Single status word for badges: EXPIRED, OUT, LOW, EXPIRING or OK. */
    public String status(int expiryWarnDays) {
        if (isExpired()) return "EXPIRED";
        if (isOutOfStock()) return "OUT";
        if (isLowStock()) return "LOW";
        if (isExpiringSoon(expiryWarnDays)) return "EXPIRING";
        return "OK";
    }

    // ------------------------------------------------------------- finance

    public double marginPercent() { return price <= 0 ? 0 : (price - costPrice) / price * 100.0; }
    public double costValue() { return costPrice * Math.max(0, quantity); }
    public double retailValue() { return price * Math.max(0, quantity); }

    // ----------------------------------------------------------- persistence

    public String[] toCsv() {
        return new String[]{String.valueOf(productId), productName, category, supplier, String.valueOf(costPrice),
                String.valueOf(price), String.valueOf(quantity), String.valueOf(reorderLevel),
                expiryDate == null ? "" : Fmt.date(expiryDate), barcode};
    }

    public static Product fromCsv(String[] f) {
        Product p = new Product(Integer.parseInt(f[0].trim()), f[1], f[2], f[3], Double.parseDouble(f[4].trim()),
                Double.parseDouble(f[5].trim()), Integer.parseInt(f[6].trim()), Integer.parseInt(f[7].trim()),
                f.length > 8 && !f[8].isBlank() ? LocalDate.parse(f[8].trim()) : null);
        if (f.length > 9) p.setBarcode(f[9]);
        return p;
    }

    /** Legacy single-line format (id,name,category,price,quantity). */
    public String getProductDetails() {
        return productId + "," + productName + "," + category + "," + price + "," + quantity;
    }

    @Override public String toString() { return productName; }
}
