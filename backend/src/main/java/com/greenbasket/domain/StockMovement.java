package com.greenbasket.domain;

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
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // EAGER: the API maps ledger rows to DTOs after the transaction has closed (open-in-view is off).
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private MovementType type;

    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "user_name", nullable = false, length = 50)
    private String userName;

    @Column(nullable = false, length = 255)
    private String note = "";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now().withNano(0);

    protected StockMovement() { }

    public StockMovement(Product product, MovementType type, int change, int balanceAfter, User user, String userName, String note) {
        this.product = product;
        this.type = type;
        this.quantityChange = change;
        this.balanceAfter = balanceAfter;
        this.user = user;
        this.userName = userName;
        this.note = note == null ? "" : (note.length() > 255 ? note.substring(0, 255) : note);
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public MovementType getType() { return type; }
    public int getQuantityChange() { return quantityChange; }
    public int getBalanceAfter() { return balanceAfter; }
    public String getUserName() { return userName; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
