package model;

public enum Role {
    MANAGER("Store Manager"),
    ASSISTANT("Sales Assistant");

    private final String label;

    Role(String label) { this.label = label; }

    public String getLabel() { return label; }

    @Override public String toString() { return label; }
}
