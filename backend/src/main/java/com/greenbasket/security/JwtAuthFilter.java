package com.greenbasket.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Endpoints a user with a temporary password may still call. */
    private static final List<String> ALLOWED_WHILE_PASSWORD_CHANGE_PENDING =
            List.of("/api/auth/change-password", "/api/auth/me", "/api/auth/logout");

    private final JwtService jwt;
    private final ProblemWriter problems;

    public JwtAuthFilter(JwtService jwt, ProblemWriter problems) {
        this.jwt = jwt;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            AppPrincipal p = jwt.verify(header.substring(7).trim());
            if (p != null) {
                if (p.mustChangePassword() && !ALLOWED_WHILE_PASSWORD_CHANGE_PENDING.contains(req.getRequestURI())) {
                    problems.write(res, 403, "Password change required",
                            "You must change your temporary password before using the system.");
                    return;
                }
                var auth = new UsernamePasswordAuthenticationToken(p, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + p.role().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(req, res);
    }
}
