package com.greenbasket.security;

import com.greenbasket.domain.Role;
import com.greenbasket.service.Actor;

/** The authenticated caller, rebuilt from the access token on every request (no DB hit). */
public record AppPrincipal(Long userId, String username, Role role, boolean mustChangePassword) {
    public Actor actor() { return new Actor(userId, username); }
}
