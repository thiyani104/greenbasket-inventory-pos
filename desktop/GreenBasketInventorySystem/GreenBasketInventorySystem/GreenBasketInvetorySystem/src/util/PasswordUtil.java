package util;

import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Base64;

/** Salted PBKDF2 password hashing - passwords are never stored in plain text. */
public final class PasswordUtil {

    private static final int ITERATIONS = 65_536;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() { }

    public static String newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String hash(String password, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(),
                    Base64.getDecoder().decode(salt), ITERATIONS, KEY_BITS);
            byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing unavailable", e);
        }
    }

    public static boolean matches(String password, String salt, String expectedHash) {
        byte[] a = hash(password, salt).getBytes();
        byte[] b = expectedHash.getBytes();
        return MessageDigest.isEqual(a, b); // constant-time comparison
    }

    /** Returns null if acceptable, otherwise a human-readable problem. */
    public static String validateStrength(String password) {
        if (password == null || password.length() < 6) return "Password must be at least 6 characters.";
        boolean letter = false, digit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) letter = true;
            if (Character.isDigit(c)) digit = true;
        }
        if (!letter || !digit) return "Password must contain both letters and numbers.";
        return null;
    }
}
