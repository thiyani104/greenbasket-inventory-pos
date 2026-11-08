package com.greenbasket.security;

import com.greenbasket.service.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() { }

    public static AppPrincipal get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof AppPrincipal p)) throw ApiException.unauthorized("Not signed in.");
        return p;
    }
}
