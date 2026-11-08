package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import util.Formats;

public class Sale {

    private final int id;
    private final LocalDateTime dateTime;
    private final String cashier;
    private final PaymentMethod payment;
    private final List<SaleItem> items;
    private final double discountPercent;
    private final double taxRate;
    private final double tendered;
    private boolean voided;
    private String voidedBy = "";
    private LocalDateTime voidedAt;

    public Sale(int id, LocalDateTime dateTime, String cashier, PaymentMethod payment,
                List<SaleItem> items, double discountPercent, double taxRate, double tendered) {
        this.id = id;
        this.dateTime = dateTime;
        this.cashier = cashier;
        this.payment = payment;
        this.items = new ArrayList<>(items);
        this.discountPercent = discountPercent;
        this.taxRate = taxRate;
        this.tendered = tendered;
    }

    public int getId() { return id; }
    public String getReceiptNumber() { return String.format("GB-%06d", id); }
    public LocalDateTime getDateTime() { return dateTime; }
    public String getCashier() { return cashier; }
    public PaymentMethod getPayment() { return payment; }
    public List<SaleItem> getItems() { return Collections.unmodifiableList(items); }
    public double getDiscountPercent() { return discountPercent; }
    public double getTaxRate() { return taxRate; }
    public double getTendered() { return tendered; }
    public boolean isVoided() { return voided; }
    public String getVoidedBy() { return voidedBy; }
    public LocalDateTime getVoidedAt() { return voidedAt; }

    public void markVoided(String by, LocalDateTime at) {
        this.voided = true;
        this.voidedBy = by == null ? "" : by;
        this.voidedAt = at;
    }

    // ---------- computed amounts (always derived, never stored) ----------
    public double getSubtotal() {
        double s = 0;
        for (SaleItem i : items) s += i.getLineTotal();
        return Formats.round2(s);
    }

    public double getDiscountAmount() { return Formats.round2(getSubtotal() * discountPercent / 100.0); }

    /** Net sales: what the shop actually earns, excluding tax. */
    public double getNetSales() { return Formats.round2(getSubtotal() - getDiscountAmount()); }

    public double getTaxAmount() { return Formats.round2(getNetSales() * taxRate / 100.0); }

    public double getTotal() { return Formats.round2(getNetSales() + getTaxAmount()); }

    public double getChange() { return payment == PaymentMethod.CASH ? Math.max(0, Formats.round2(tendered - getTotal())) : 0; }

    public int getItemCount() {
        int n = 0;
        for (SaleItem i : items) n += i.getQuantity();
        return n;
    }

    public double getCostOfGoods() {
        double c = 0;
        for (SaleItem i : items) c += i.getLineCost();
        return Formats.round2(c);
    }

    public double getProfit() { return Formats.round2(getNetSales() - getCostOfGoods()); }
}
