package model;

import java.time.LocalDateTime;

/** One entry in the stock ledger: who changed which product's quantity, by how much, and why. */
public class StockMovement {

    public static final String[] CSV_HEADER =
            {"time", "productId", "productName", "type", "change", "quantityAfter", "user", "note"};

    private final LocalDateTime time;
    private final int productId;
    private final String productName;
    private final String type;
    private final int change;
    private final int quantityAfter;
    private final String user;
    private final String note;

    public StockMovement(LocalDateTime time, int productId, String productName, String type,
                         int change, int quantityAfter, String user, String note) {
        this.time = time;
        this.productId = productId;
        this.productName = productName;
        this.type = type;
        this.change = change;
        this.quantityAfter = quantityAfter;
        this.user = user;
        this.note = note == null ? "" : note;
    }

    public LocalDateTime getTime() { return time; }
    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getType() { return type; }
    public int getChange() { return change; }
    public int getQuantityAfter() { return quantityAfter; }
    public String getUser() { return user; }
    public String getNote() { return note; }

    public String[] toCsv() {
        return new String[]{util.Fmt.dateTime(time), String.valueOf(productId), productName, type,
                String.valueOf(change), String.valueOf(quantityAfter), user, note};
    }

    public static StockMovement fromCsv(String[] f) {
        return new StockMovement(util.Fmt.parseDateTime(f[0]), Integer.parseInt(f[1]), f[2], f[3],
                Integer.parseInt(f[4]), Integer.parseInt(f[5]), f[6], f.length > 7 ? f[7] : "");
    }
}
