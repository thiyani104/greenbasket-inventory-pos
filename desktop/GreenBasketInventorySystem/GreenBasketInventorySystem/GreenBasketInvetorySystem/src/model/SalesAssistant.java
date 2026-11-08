package model;

import java.time.LocalDateTime;

/** Till operator: can sell, look up stock and view their own sales. */
public class SalesAssistant extends User {

    SalesAssistant(int id, String username, String fullName, String salt, String hash,
                   boolean active, LocalDateTime lastLogin, LocalDateTime created) {
        super(id, username, fullName, Role.ASSISTANT, salt, hash, active, lastLogin, created);
    }

    public static SalesAssistant create(int id, String username, String fullName, String password) {
        String[] h = hashed(password);
        return new SalesAssistant(id, username, fullName, h[0], h[1], true, null, LocalDateTime.now());
    }
}
