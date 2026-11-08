package model;

import java.time.LocalDateTime;

/** One line in the stock ledger. */
public class StockMovement {

    private final int id;
    private final LocalDateTime dateTime;
    private final int productId;
    private final String productName;
    private final MovementType type;
    private final int change;
    private final int balanceAfter;
    private final String user;
    private final String note;

    public StockMovement(int id, LocalDateTime dateTime, int productId, String productName,
                         MovementType type, int change, int balanceAfter, String user, String note) {
        this.id = id;
        this.dateTime = dateTime;
        this.productId = productId;
        this.productName = productName;
        this.type = type;
        this.change = change;
        this.balanceAfter = balanceAfter;
        this.user = user == null ? "" : user;
        this.note = note == null ? "" : note;
    }

    public int getId() { return id; }
    public LocalDateTime getDateTime() { return dateTime; }
    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public MovementType getType() { return type; }
    public int getChange() { return change; }
    public int getBalanceAfter() { return balanceAfter; }
    public String getUser() { return user; }
    public String getNote() { return note; }
}
