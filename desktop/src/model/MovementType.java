package model;

public enum MovementType {
    INITIAL("Opening stock"),
    RECEIVE("Stock received"),
    SALE("Sale"),
    VOID_RETURN("Sale voided"),
    WASTAGE("Write-off"),
    CORRECTION("Stock count");

    private final String label;

    MovementType(String label) { this.label = label; }

    public String getLabel() { return label; }

    @Override public String toString() { return label; }
}
