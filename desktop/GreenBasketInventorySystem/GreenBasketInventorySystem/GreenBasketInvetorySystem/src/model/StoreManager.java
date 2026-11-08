package model;

import java.time.LocalDateTime;

/** Full access: purchasing, reports, users, settings and every inventory action. */
public class StoreManager extends User {

    StoreManager(int id, String username, String fullName, String salt, String hash,
                 boolean active, LocalDateTime lastLogin, LocalDateTime created) {
        super(id, username, fullName, Role.MANAGER, salt, hash, active, lastLogin, created);
    }

    public static StoreManager create(int id, String username, String fullName, String password) {
        String[] h = hashed(password);
        return new StoreManager(id, username, fullName, h[0], h[1], true, null, LocalDateTime.now());
    }
}
