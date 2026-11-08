package com.greenbasket.service;

import com.greenbasket.domain.PasswordResetToken;
import com.greenbasket.domain.RefreshToken;
import com.greenbasket.domain.User;
import com.greenbasket.repository.PasswordResetTokenRepository;
import com.greenbasket.repository.RefreshTokenRepository;
import com.greenbasket.repository.UserRepository;
import com.greenbasket.security.JwtService;
import com.greenbasket.security.PasswordResetNotifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, lockout, rotating refresh tokens, password change and reset.
 * <p>
 * Methods that record a failure and then throw are marked {@code noRollbackFor = ApiException.class}; without that
 * the failed-attempt counter (and refresh-token reuse revocation) would be rolled back together with the exception.
 */
@Service
public class AuthService {

    public record UserInfo(Long id, String username, String fullName, String email, String role, boolean mustChangePassword) { }

    public record TokenPair(String accessToken, String refreshToken, String tokenType, long expiresInSeconds, UserInfo user) { }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final ActivityService log;
    private final PasswordResetNotifier notifier;
    private final int maxFailed;
    private final Duration lockFor;
    private final Duration refreshTtl;
    private final Duration resetTtl;
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordResetTokenRepository resetTokens,
                       PasswordEncoder encoder, JwtService jwt, ActivityService log, PasswordResetNotifier notifier,
                       @Value("${app.security.max-failed-attempts:5}") int maxFailed,
                       @Value("${app.security.lock-minutes:15}") long lockMinutes,
                       @Value("${app.jwt.refresh-days:14}") long refreshDays,
                       @Value("${app.security.reset-token-minutes:30}") long resetMinutes) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.log = log;
        this.notifier = notifier;
        this.maxFailed = maxFailed;
        this.lockFor = Duration.ofMinutes(lockMinutes);
        this.refreshTtl = Duration.ofDays(refreshDays);
        this.resetTtl = Duration.ofMinutes(resetMinutes);
        this.dummyHash = encoder.encode("timing-equaliser-" + RANDOM.nextInt());
    }

    // ------------------------------------------------------------------ login

    @Transactional(noRollbackFor = ApiException.class)
    public TokenPair login(String username, String password) {
        if (username == null || password == null || username.isBlank()) throw ApiException.badRequest("Username and password are required.");
        Optional<User> found = users.findByUsernameKey(User.keyOf(username));
        if (found.isEmpty()) {
            encoder.matches(password, dummyHash);          // same work as a real check, so timing does not reveal valid names
            log.logAlways(username.trim(), "Login failed", "unknown user");
            throw ApiException.unauthorized("Invalid username or password.");
        }
        User u = found.get();
        LocalDateTime now = LocalDateTime.now();
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)) {
            long mins = Math.max(1, Duration.between(now, u.getLockedUntil()).toMinutes() + 1);
            log.logAlways(u.getUsername(), "Login blocked", "account locked");
            throw ApiException.locked("Too many failed attempts. Try again in about " + mins + " minute(s).");
        }
        if (!encoder.matches(password, u.getPasswordHash())) {
            int attempts = u.getFailedAttempts() + 1;
            u.setFailedAttempts(attempts);
            if (attempts >= maxFailed) {
                u.setLockedUntil(now.plus(lockFor));
                u.setFailedAttempts(0);
                log.logAlways(u.getUsername(), "Account locked", maxFailed + " failed attempts");
                throw ApiException.locked("Too many failed attempts. The account is locked for " + lockFor.toMinutes() + " minutes.");
            }
            log.logAlways(u.getUsername(), "Login failed", "bad password (" + attempts + "/" + maxFailed + ")");
            throw ApiException.unauthorized("Invalid username or password.");
        }
        if (!u.isActive()) {
            log.logAlways(u.getUsername(), "Login blocked", "account disabled");
            throw ApiException.forbidden("This account has been disabled. Ask the store manager.");
        }
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        u.setLastLogin(now.withNano(0));
        if (encoder.upgradeEncoding(u.getPasswordHash())) {
            u.setPasswordHash(encoder.encode(password));      // legacy PBKDF2 -> bcrypt, transparently
        }
        log.log(new Actor(u.getId(), u.getUsername()), "Login", "");
        return issue(u);
    }

    // ------------------------------------------------------------------ refresh / logout

    @Transactional(noRollbackFor = ApiException.class)
    public TokenPair refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw ApiException.unauthorized("Refresh token missing.");
        RefreshToken t = refreshTokens.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token."));
        User u = t.getUser();
        if (t.isRevoked()) {
            // A rotated token was presented again: assume theft and end every session of this user.
            refreshTokens.revokeAllForUser(u.getId());
            log.logAlways(u.getUsername(), "Refresh token reuse", "all sessions revoked");
            throw ApiException.unauthorized("Session is no longer valid. Please sign in again.");
        }
        if (t.getExpiresAt().isBefore(LocalDateTime.now())) throw ApiException.unauthorized("Session expired. Please sign in again.");
        if (!u.isActive()) throw ApiException.forbidden("This account has been disabled.");
        t.setRevoked(true);
        return issue(u);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokens.findByTokenHash(sha256(rawToken)).ifPresent(t -> t.setRevoked(true));
    }

    // ------------------------------------------------------------------ password change / reset

    @Transactional
    public TokenPair changePassword(Long userId, String current, String next) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Account no longer exists."));
        if (!encoder.matches(current == null ? "" : current, u.getPasswordHash())) throw ApiException.badRequest("Current password is incorrect.");
        UserService.requireStrongPassword(next);
        if (next.equals(current)) throw ApiException.badRequest("New password must be different from the current one.");
        u.setPasswordHash(encoder.encode(next));
        u.setMustChangePassword(false);
        refreshTokens.revokeAllForUser(u.getId());
        log.log(new Actor(u.getId(), u.getUsername()), "Change password", "");
        return issue(u);
    }

    /** Always succeeds from the caller's point of view, so it cannot be used to discover which accounts exist. */
    @Transactional
    public void forgotPassword(String usernameOrEmail) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) return;
        String q = usernameOrEmail.trim();
        Optional<User> found = q.contains("@") ? users.findByEmail(q.toLowerCase()) : users.findByUsernameKey(User.keyOf(q));
        if (found.isEmpty()) return;
        User u = found.get();
        if (!u.isActive() || u.getEmail() == null) return;
        resetTokens.invalidateAllForUser(u.getId());
        String raw = randomToken();
        resetTokens.save(new PasswordResetToken(u, sha256(raw), LocalDateTime.now().plus(resetTtl)));
        log.log(new Actor(u.getId(), u.getUsername()), "Password reset requested", "");
        notifier.send(u.getUsername(), u.getEmail(), raw);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken t = resetTokens.findByTokenHash(sha256(rawToken == null ? "" : rawToken))
                .orElseThrow(() -> ApiException.badRequest("This reset link is invalid."));
        if (t.isUsed() || t.getExpiresAt().isBefore(LocalDateTime.now())) throw ApiException.badRequest("This reset link has expired or was already used.");
        UserService.requireStrongPassword(newPassword);
        User u = t.getUser();
        u.setPasswordHash(encoder.encode(newPassword));
        u.setMustChangePassword(false);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        t.setUsed(true);
        refreshTokens.revokeAllForUser(u.getId());
        log.log(new Actor(u.getId(), u.getUsername()), "Password reset", "");
    }

    @Transactional(readOnly = true)
    public UserInfo me(Long userId) {
        return info(users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Account no longer exists.")));
    }

    // ------------------------------------------------------------------ helpers

    private TokenPair issue(User u) {
        String raw = randomToken();
        refreshTokens.save(new RefreshToken(u, sha256(raw), LocalDateTime.now().plus(refreshTtl)));
        return new TokenPair(jwt.createAccessToken(u), raw, "Bearer", jwt.accessTtl().toSeconds(), info(u));
    }

    private static UserInfo info(User u) {
        return new UserInfo(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getRole().name(), u.isMustChangePassword());
    }

    private static String randomToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Tokens are stored only as SHA-256 digests, so a database leak does not leak usable sessions. */
    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
