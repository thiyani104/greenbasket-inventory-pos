package com.greenbasket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_log")
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(nullable = false, length = 500)
    private String details = "";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now().withNano(0);

    protected ActivityLog() { }

    public ActivityLog(String username, String action, String details) {
        this.username = username;
        this.action = action.length() > 60 ? action.substring(0, 60) : action;
        String d = details == null ? "" : details;
        this.details = d.length() > 500 ? d.substring(0, 500) : d;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { if (v != null) this.createdAt = v; }
}
