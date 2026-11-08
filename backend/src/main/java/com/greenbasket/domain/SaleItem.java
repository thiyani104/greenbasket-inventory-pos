package com.greenbasket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** One receipt line. Name, category and prices are snapshots so history survives later edits. */
@Entity
@Table(name = "sale_items")
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Read-only copy of the FK so receipts can be rendered without initialising the lazy product proxy. */
    @Column(name = "product_id", insertable = false, updatable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 80)
    private String productName;

    @Column(name = "category_name", nullable = false, length = 60)
    private String categoryName;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(nullable = false)
    private int quantity;

    protected SaleItem() { }

    public SaleItem(Product product, int quantity) {
        this.product = product;
        this.productName = product.getName();
        this.categoryName = product.getCategory().getName();
        this.unitPrice = product.getPrice();
        this.unitCost = product.getCost();
        this.quantity = quantity;
    }

    /** Rebuilds a historical line from imported data, keeping the prices that were charged back then. */
    public static SaleItem snapshot(Product product, String name, String category, BigDecimal price, BigDecimal cost, int quantity) {
        SaleItem i = new SaleItem(product, quantity);
        i.productName = name.isEmpty() ? product.getName() : name;
        i.categoryName = category.isEmpty() ? product.getCategory().getName() : category;
        i.unitPrice = price;
        i.unitCost = cost;
        return i;
    }

    public BigDecimal lineTotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }

    public BigDecimal lineCost() { return unitCost.multiply(BigDecimal.valueOf(quantity)); }

    public Long getId() { return id; }
    public Sale getSale() { return sale; }
    void setSale(Sale s) { this.sale = s; }
    public Product getProduct() { return product; }
    public Long getProductId() { return productId != null ? productId : product.getId(); }
    public String getProductName() { return productName; }
    public String getCategoryName() { return categoryName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getUnitCost() { return unitCost; }
    public int getQuantity() { return quantity; }
}
