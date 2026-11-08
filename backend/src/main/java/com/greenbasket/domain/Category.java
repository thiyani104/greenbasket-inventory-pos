package com.greenbasket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "name_key", nullable = false, length = 60)
    private String nameKey;

    protected Category() { }

    public Category(String name) { setName(name); }

    public static String keyOf(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v.trim(); this.nameKey = keyOf(v); }
}
