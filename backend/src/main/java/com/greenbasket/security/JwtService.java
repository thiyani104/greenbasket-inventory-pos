package com.greenbasket.security;

import com.greenbasket.domain.Role;
import com.greenbasket.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Issues and verifies short-lived signed access tokens (HS256). Refresh tokens are opaque and live in the DB. */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-minutes:15}") long accessMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be at least 32 bytes long.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(accessMinutes);
    }

    public Duration accessTtl() { return accessTtl; }

    public String createAccessToken(User u) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(u.getUsername())
                .claim("uid", u.getId())
                .claim("role", u.getRole().name())
                .claim("mcp", u.isMustChangePassword())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** Returns the principal, or null if the token is missing, malformed, tampered with or expired. */
    public AppPrincipal verify(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Number uid = c.get("uid", Number.class);
            Boolean mcp = c.get("mcp", Boolean.class);
            return new AppPrincipal(uid == null ? null : uid.longValue(), c.getSubject(),
                    Role.valueOf(c.get("role", String.class)), Boolean.TRUE.equals(mcp));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
