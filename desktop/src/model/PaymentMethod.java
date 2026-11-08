package model;

public enum PaymentMethod {
    CASH("Cash"), CARD("Card"), MOBILE("Mobile pay");

    private final String label;

    PaymentMethod(String label) { this.label = label; }

    public String getLabel() { return label; }

    @Override public String toString() { return label; }
}
