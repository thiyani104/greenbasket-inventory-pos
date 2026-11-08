package com.greenbasket.service;

import com.greenbasket.domain.Role;
import com.greenbasket.domain.User;
import com.greenbasket.repository.RefreshTokenRepository;
import com.greenbasket.repository.UserRepository;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff accounts (manager-only operations). Authentication lives in {@code AuthService}. */
@Service
public class UserService {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,30}");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final ActivityService log;

    public UserService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder, ActivityService log) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.log = log;
    }

    @Transactional(readOnly = true)
    public List<User> list() { return users.findAllByOrderByUsernameKeyAsc(); }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found."));
    }

    /** Password strength rule shared by create, change and reset. */
    public static void requireStrongPassword(String pw) {
        if (pw == null || pw.length() < 8) throw ApiException.badRequest("Password must be at least 8 characters.");
        if (pw.length() > 100) throw ApiException.badRequest("Password is too long.");
        boolean letter = pw.chars().anyMatch(Character::isLetter), digit = pw.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) throw ApiException.badRequest("Password must contain letters and numbers.");
    }

    @Transactional
    public User create(String username, String fullName, String email, Role role, String password, Actor actor) {
        if (username == null || !USERNAME.matcher(username.trim()).matches()) {
            throw ApiException.badRequest("Username must be 3-30 characters: letters, numbers, dot, dash or underscore.");
        }
        if (fullName == null || fullName.isBlank()) throw ApiException.badRequest("Full name is required.");
        if (role == null) throw ApiException.badRequest("Role is required.");
        checkEmail(email);
        requireStrongPassword(password);
        if (users.existsByUsernameKey(User.keyOf(username))) throw ApiException.conflict("Username '" + username.trim() + "' is taken.");
        User u = new User(username, fullName.trim(), role, encoder.encode(password));
        u.setEmail(email);
        u.setMustChangePassword(true);
        u = users.save(u);
        log.log(actor, "Add user", u.getUsername() + " (" + role + ")");
        return u;
    }

    @Transactional
    public User update(Long id, String fullName, String email, Role role, Boolean active, Actor actor) {
        User u = get(id);
        if (fullName != null) {
            if (fullName.isBlank()) throw ApiException.badRequest("Full name is required.");
            u.setFullName(fullName.trim());
        }
        if (email != null) {
            checkEmail(email);
            u.setEmail(email);
        }
        boolean losesManager = (role != null && role != Role.MANAGER) || (active != null && !active);
        if (u.getRole() == Role.MANAGER && u.isActive() && losesManager && users.countByRoleAndActiveTrue(Role.MANAGER) <= 1) {
            throw ApiException.conflict("There must always be at least one active store manager.");
        }
        if (actor.userId() != null && actor.userId().equals(id) && active != null && !active) {
            throw ApiException.conflict("You cannot deactivate your own account.");
        }
        if (role != null) u.setRole(role);
        if (active != null) {
            u.setActive(active);
            if (!active) refreshTokens.revokeAllForUser(id);
        }
        log.log(actor, "Edit user", u.getUsername());
        return u;
    }

    /** Manager sets a temporary password; the user must change it at next login. */
    @Transactional
    public void resetPassword(Long id, String temporaryPassword, Actor actor) {
        requireStrongPassword(temporaryPassword);
        User u = get(id);
        u.setPasswordHash(encoder.encode(temporaryPassword));
        u.setMustChangePassword(true);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        refreshTokens.revokeAllForUser(id);
        log.log(actor, "Reset password", u.getUsername());
    }

    @Transactional
    public User unlock(Long id, Actor actor) {
        User u = get(id);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        log.log(actor, "Unlock user", u.getUsername());
        return u;
    }

    private static void checkEmail(String email) {
        if (email != null && !email.isBlank() && !EMAIL.matcher(email.trim()).matches()) {
            throw ApiException.badRequest("Email address looks invalid.");
        }
    }
}
