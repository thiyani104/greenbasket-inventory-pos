package model;

/** One line on a purchase order. */
public class PurchaseItem {

    private final int productId;
    private final String name;
    private final int quantity;
    private final double unitCost;

    public PurchaseItem(int productId, String name, int quantity, double unitCost) {
        this.productId = productId;
        this.name = name;
        this.quantity = quantity;
        this.unitCost = unitCost;
    }

    public int getProductId() { return productId; }
    public String getName() { return name; }
    public int getQuantity() { return quantity; }
    public double getUnitCost() { return unitCost; }
    public double lineTotal() { return quantity * unitCost; }
}
