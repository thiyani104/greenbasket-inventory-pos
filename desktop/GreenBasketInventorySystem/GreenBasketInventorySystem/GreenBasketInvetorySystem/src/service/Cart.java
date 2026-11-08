package service;

import java.util.ArrayList;
import java.util.List;
import model.Product;
import model.SaleItem;

/** The basket being rung up at the till. Pure logic - no UI, no disk. */
public class Cart {

    private final List<SaleItem> lines = new ArrayList<>();
    private double discountPercent = 0;
    private final double taxRate;

    public Cart(double taxRate) {
        this.taxRate = taxRate;
    }

    public List<SaleItem> getLines() { return lines; }
    public boolean isEmpty() { return lines.isEmpty(); }

    public SaleItem find(int productId) {
        for (SaleItem i : lines) if (i.getProductId() == productId) return i;
        return null;
    }

    /** Adds {@code qty} units, merging with an existing line and respecting available stock. */
    public void add(Product p, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be at least 1.");
        if (p.isExpired()) throw new IllegalArgumentException("\"" + p.getProductName() + "\" has expired and cannot be sold.");
        SaleItem existing = find(p.getProductId());
        int already = existing == null ? 0 : existing.getQuantity();
        if (already + qty > p.getQuantity()) {
            throw new IllegalArgumentException("Only " + p.getQuantity() + " of \"" + p.getProductName()
                    + "\" in stock" + (already > 0 ? " (" + already + " already in cart)." : "."));
        }
        if (existing != null) {
            existing.setQuantity(already + qty);
        } else {
            lines.add(new SaleItem(p.getProductId(), p.getProductName(), p.getCategory(), qty, p.getPrice(), p.getCostPrice()));
        }
    }

    public void setQuantity(Product p, int qty) {
        SaleItem line = find(p.getProductId());
        if (line == null) return;
        if (qty <= 0) {
            lines.remove(line);
            return;
        }
        if (qty > p.getQuantity()) {
            throw new IllegalArgumentException("Only " + p.getQuantity() + " of \"" + p.getProductName() + "\" in stock.");
        }
        line.setQuantity(qty);
    }

    public void remove(int productId) { lines.removeIf(i -> i.getProductId() == productId); }
    public void clear() { lines.clear(); discountPercent = 0; }

    public double getDiscountPercent() { return discountPercent; }

    public void setDiscountPercent(double pct) {
        if (pct < 0 || pct > 100) throw new IllegalArgumentException("Discount must be between 0 and 100%.");
        this.discountPercent = pct;
    }

    public double getTaxRate() { return taxRate; }

    public int itemCount() {
        int n = 0;
        for (SaleItem i : lines) n += i.getQuantity();
        return n;
    }

    public double subtotal() {
        double s = 0;
        for (SaleItem i : lines) s += i.lineTotal();
        return round(s);
    }

    public double discount() { return round(subtotal() * discountPercent / 100.0); }
    public double tax() { return round((subtotal() - discount()) * taxRate / 100.0); }
    public double total() { return round(subtotal() - discount() + tax()); }

    static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
