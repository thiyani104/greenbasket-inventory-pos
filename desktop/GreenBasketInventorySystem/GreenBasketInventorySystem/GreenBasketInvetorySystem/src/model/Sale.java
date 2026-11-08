package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A completed (or voided) checkout. */
public class Sale {

    public static final String COMPLETED = "COMPLETED";
    public static final String VOIDED = "VOIDED";

    public static final String[] CSV_HEADER =
            {"id", "time", "cashier", "customer", "payment", "subtotal", "discount", "tax", "total", "tendered", "status"};
    public static final String[] ITEM_HEADER =
            {"saleId", "productId", "name", "category", "quantity", "unitPrice", "unitCost"};

    private final int id;
    private final LocalDateTime time;
    private final String cashier;
    private final String customer;
    private final String payment;
    private final double subtotal;
    private final double discount;
    private final double tax;
    private final double total;
    private final double tendered;
    private String status;
    private final List<SaleItem> items = new ArrayList<>();

    public Sale(int id, LocalDateTime time, String cashier, String customer, String payment,
                double subtotal, double discount, double tax, double total, double tendered, String status) {
        this.id = id;
        this.time = time;
        this.cashier = cashier;
        this.customer = customer == null ? "" : customer;
        this.payment = payment;
        this.subtotal = subtotal;
        this.discount = discount;
        this.tax = tax;
        this.total = total;
        this.tendered = tendered;
        this.status = status;
    }

    public int getId() { return id; }
    public LocalDateTime getTime() { return time; }
    public String getCashier() { return cashier; }
    public String getCustomer() { return customer; }
    public String getPayment() { return payment; }
    public double getSubtotal() { return subtotal; }
    public double getDiscount() { return discount; }
    public double getTax() { return tax; }
    public double getTotal() { return total; }
    public double getTendered() { return tendered; }
    public double getChange() { return Math.max(0, tendered - total); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isVoided() { return VOIDED.equals(status); }
    public List<SaleItem> getItems() { return items; }

    public int itemCount() {
        int n = 0;
        for (SaleItem i : items) n += i.getQuantity();
        return n;
    }

    /** Profit after discount (tax is not profit). */
    public double profit() {
        double cost = 0;
        for (SaleItem i : items) cost += i.getUnitCost() * i.getQuantity();
        return subtotal - discount - cost;
    }

    public String[] toCsv() {
        return new String[]{String.valueOf(id), util.Fmt.dateTime(time), cashier, customer, payment,
                String.valueOf(subtotal), String.valueOf(discount), String.valueOf(tax),
                String.valueOf(total), String.valueOf(tendered), status};
    }
}
