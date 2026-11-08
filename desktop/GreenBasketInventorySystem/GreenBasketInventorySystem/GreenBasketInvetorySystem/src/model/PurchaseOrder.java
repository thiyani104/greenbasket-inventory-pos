package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import util.Fmt;

/** An order placed with a supplier. ORDERED -> RECEIVED (stock added) or CANCELLED. */
public class PurchaseOrder {

    public static final String ORDERED = "ORDERED";
    public static final String RECEIVED = "RECEIVED";
    public static final String CANCELLED = "CANCELLED";

    public static final String[] CSV_HEADER = {"id", "created", "createdBy", "supplier", "status", "received", "note"};
    public static final String[] ITEM_HEADER = {"poId", "productId", "name", "quantity", "unitCost"};

    private final int id;
    private final LocalDateTime created;
    private final String createdBy;
    private final String supplier;
    private String status;
    private LocalDateTime received;
    private final String note;
    private final List<PurchaseItem> items = new ArrayList<>();

    public PurchaseOrder(int id, LocalDateTime created, String createdBy, String supplier,
                         String status, LocalDateTime received, String note) {
        this.id = id;
        this.created = created;
        this.createdBy = createdBy;
        this.supplier = supplier;
        this.status = status;
        this.received = received;
        this.note = note == null ? "" : note;
    }

    public int getId() { return id; }
    public LocalDateTime getCreated() { return created; }
    public String getCreatedBy() { return createdBy; }
    public String getSupplier() { return supplier; }
    public String getStatus() { return status; }
    public LocalDateTime getReceived() { return received; }
    public String getNote() { return note; }
    public List<PurchaseItem> getItems() { return items; }
    public boolean isOpen() { return ORDERED.equals(status); }

    public void markReceived() { this.status = RECEIVED; this.received = LocalDateTime.now(); }
    public void markCancelled() { this.status = CANCELLED; }

    public double total() {
        double t = 0;
        for (PurchaseItem i : items) t += i.lineTotal();
        return t;
    }

    public int units() {
        int n = 0;
        for (PurchaseItem i : items) n += i.getQuantity();
        return n;
    }

    public String[] toCsv() {
        return new String[]{String.valueOf(id), Fmt.dateTime(created), createdBy, supplier, status,
                received == null ? "" : Fmt.dateTime(received), note};
    }
}
