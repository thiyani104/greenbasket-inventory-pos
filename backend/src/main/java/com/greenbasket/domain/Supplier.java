package com.greenbasket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;

@Entity
@Table(name = "suppliers")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "name_key", nullable = false, length = 100)
    private String nameKey;

    @Column(length = 40)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 255)
    private String address;

    protected Supplier() { }

    public Supplier(String name) { setName(name); }

    public static String keyOf(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v.trim(); this.nameKey = keyOf(v); }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { this.address = v; }
}
