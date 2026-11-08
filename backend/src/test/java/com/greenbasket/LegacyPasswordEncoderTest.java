package com.greenbasket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.greenbasket.security.LegacyPbkdf2PasswordEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The fixture hash was produced by the Swing app's own PasswordUtil, so this proves the two implementations agree. */
class LegacyPasswordEncoderTest {

    private final LegacyPbkdf2PasswordEncoder enc = new LegacyPbkdf2PasswordEncoder();

    @Test
    void verifiesAHashWrittenByTheDesktopApp() throws Exception {
        String line = Files.readAllLines(Path.of(getClass().getResource("/swing-data/users.csv").toURI())).get(1);
        String hash = line.split(",")[4];
        assertTrue(enc.matches("Legacy@123", hash));
        assertFalse(enc.matches("legacy@123", hash));
        assertFalse(enc.matches("Legacy@123", "garbage"));
        assertFalse(enc.matches("Legacy@123", hash.replace("$", "")));
    }

    @Test
    void cannotEncode() {
        assertThrows(UnsupportedOperationException.class, () -> enc.encode("x"));
    }
}
