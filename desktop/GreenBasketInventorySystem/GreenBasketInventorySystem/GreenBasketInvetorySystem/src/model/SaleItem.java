package model;

/** One line on a sale. Name/price/cost are snapshots so history survives product edits. */
public class SaleItem {

    private final int productId;
    private final String name;
    private final String category;
    private int quantity;
    private final double unitPrice;
    private final double unitCost;

    public SaleItem(int productId, String name, String category, int quantity, double unitPrice, double unitCost) {
        this.productId = productId;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.unitCost = unitCost;
    }

    public int getProductId() { return productId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int q) { this.quantity = q; }
    public double getUnitPrice() { return unitPrice; }
    public double getUnitCost() { return unitCost; }
    public double lineTotal() { return unitPrice * quantity; }
    public double lineProfit() { return (unitPrice - unitCost) * quantity; }
}
