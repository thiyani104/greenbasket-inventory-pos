package com.greenbasket.service;

/** Who is performing an action. The name is stored on ledger rows so history survives user changes. */
public record Actor(Long userId, String username) {
    public static Actor system() { return new Actor(null, "system"); }
}
