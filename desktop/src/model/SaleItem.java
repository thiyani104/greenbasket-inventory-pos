package model;

import util.Formats;

/** A line on a sale. Name / price / cost are snapshots so history never changes. */
public class SaleItem {

    private final int productId;
    private final String productName;
    private final String category;
    private final double unitPrice;
    private final double unitCost;
    private final int quantity;

    public SaleItem(int productId, String productName, String category,
                    double unitPrice, double unitCost, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.category = category;
        this.unitPrice = unitPrice;
        this.unitCost = unitCost;
        this.quantity = quantity;
    }

    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public double getUnitPrice() { return unitPrice; }
    public double getUnitCost() { return unitCost; }
    public int getQuantity() { return quantity; }

    public double getLineTotal() { return Formats.round2(unitPrice * quantity); }
    public double getLineCost() { return Formats.round2(unitCost * quantity); }
}
