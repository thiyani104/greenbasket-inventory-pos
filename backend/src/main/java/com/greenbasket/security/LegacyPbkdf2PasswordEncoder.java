package com.greenbasket.security;

import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Verifies hashes written by the Swing desktop app: {@code base64(salt)$base64(PBKDF2-HMAC-SHA256, 65536 iterations)}.
 * Encoding is intentionally unsupported; users are upgraded to bcrypt on their next successful login.
 */
public class LegacyPbkdf2PasswordEncoder implements PasswordEncoder {

    private static final int ITERATIONS = 65_536;
    private static final int KEY_BITS = 256;

    @Override
    public String encode(CharSequence raw) {
        throw new UnsupportedOperationException("Legacy hashes are verify-only.");
    }

    @Override
    public boolean matches(CharSequence raw, String stored) {
        if (raw == null || stored == null) return false;
        String[] parts = stored.split("\\$");
        if (parts.length != 2) return false;
        try {
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expected = Base64.getDecoder().decode(parts[1]);
            PBEKeySpec spec = new PBEKeySpec(raw.toString().toCharArray(), salt, ITERATIONS, KEY_BITS);
            byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }
}
