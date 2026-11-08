package com.greenbasket.security;

/** Delivers a password-reset token to the user. Phase 5 replaces the default with real e-mail. */
public interface PasswordResetNotifier {
    void send(String username, String email, String token);
}
