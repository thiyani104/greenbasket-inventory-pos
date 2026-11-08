package com.greenbasket.web;

import com.greenbasket.security.CurrentUser;
import com.greenbasket.service.AuthService;
import com.greenbasket.web.Dtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/login")
    public AuthService.TokenPair login(@Valid @RequestBody LoginRequest r) { return auth.login(r.username(), r.password()); }

    @PostMapping("/refresh")
    public AuthService.TokenPair refresh(@Valid @RequestBody RefreshRequest r) { return auth.refresh(r.refreshToken()); }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) LogoutRequest r) { auth.logout(r == null ? null : r.refreshToken()); }

    @GetMapping("/me")
    public AuthService.UserInfo me() { return auth.me(CurrentUser.get().userId()); }

    @PostMapping("/change-password")
    public AuthService.TokenPair change(@Valid @RequestBody ChangePasswordRequest r) {
        return auth.changePassword(CurrentUser.get().userId(), r.currentPassword(), r.newPassword());
    }

    /** Always 202, whether or not the account exists. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> forgot(@Valid @RequestBody ForgotPasswordRequest r) {
        auth.forgotPassword(r.usernameOrEmail());
        return Map.of("message", "If that account exists and has an e-mail address, a reset link has been sent.");
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetPasswordRequest r) { auth.resetPassword(r.token(), r.newPassword()); }
}
