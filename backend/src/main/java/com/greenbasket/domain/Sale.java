package com.greenbasket.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_no", length = 20)
    private String receiptNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_id")
    private User cashier;

    @Column(name = "cashier_name", nullable = false, length = 50)
    private String cashierName;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10)
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal tendered = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean voided;

    @Column(name = "voided_by", length = 50)
    private String voidedBy;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "void_reason", length = 255)
    private String voidReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now().withNano(0);

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<SaleItem> items = new ArrayList<>();

    protected Sale() { }

    public Sale(User cashier, String cashierName, PaymentMethod method, BigDecimal discountPercent,
                BigDecimal taxRate, BigDecimal tendered) {
        this.cashier = cashier;
        this.cashierName = cashierName;
        this.paymentMethod = method;
        this.discountPercent = discountPercent;
        this.taxRate = taxRate;
        this.tendered = tendered;
    }

    public void addItem(SaleItem item) {
        item.setSale(this);
        items.add(item);
    }

    // ---------------------------------------------------------------- money
    public BigDecimal subtotal() {
        return items.stream().map(SaleItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal discountAmount() {
        return subtotal().multiply(discountPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal taxAmount() {
        return subtotal().subtract(discountAmount()).multiply(taxRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal total() {
        return subtotal().subtract(discountAmount()).add(taxAmount());
    }

    public BigDecimal change() {
        return tendered.subtract(total()).max(BigDecimal.ZERO);
    }

    /** Cost of goods sold. */
    public BigDecimal costOfGoods() {
        return items.stream().map(SaleItem::lineCost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Profit after discount, before tax (tax is passed through to the authorities). */
    public BigDecimal profit() {
        return subtotal().subtract(discountAmount()).subtract(costOfGoods());
    }

    public int unitCount() { return items.stream().mapToInt(SaleItem::getQuantity).sum(); }

    public void markVoided(String by, String reason) {
        this.voided = true;
        this.voidedBy = by;
        this.voidedAt = LocalDateTime.now().withNano(0);
        this.voidReason = reason == null ? "" : reason;
    }

    public Long getId() { return id; }
    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String v) { this.receiptNo = v; }
    public User getCashier() { return cashier; }
    public String getCashierName() { return cashierName; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getTendered() { return tendered; }
    public void setTendered(BigDecimal v) { this.tendered = v; }
    public boolean isVoided() { return voided; }
    public String getVoidedBy() { return voidedBy; }
    public LocalDateTime getVoidedAt() { return voidedAt; }
    public void setVoidedAt(LocalDateTime v) { this.voidedAt = v; }
    public String getVoidReason() { return voidReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { if (v != null) this.createdAt = v; }
    public List<SaleItem> getItems() { return items; }
}
