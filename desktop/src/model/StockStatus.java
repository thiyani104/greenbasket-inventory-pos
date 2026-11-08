package model;

public enum StockStatus {
    EXPIRED("Expired"),
    OUT("Out of stock"),
    LOW("Low stock"),
    EXPIRING("Expiring soon"),
    OK("In stock");

    private final String label;

    StockStatus(String label) { this.label = label; }

    public String getLabel() { return label; }

    @Override public String toString() { return label; }
}
