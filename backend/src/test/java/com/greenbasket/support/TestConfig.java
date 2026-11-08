package com.greenbasket.support;

import com.greenbasket.security.PasswordResetNotifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestConfig {

    /** Captures reset tokens instead of logging them, so tests can complete the reset flow. */
    public static class CapturingNotifier implements PasswordResetNotifier {
        public final Map<String, String> lastToken = new ConcurrentHashMap<>();

        @Override
        public void send(String username, String email, String token) { lastToken.put(username, token); }
    }

    @Bean
    @Primary
    public CapturingNotifier capturingNotifier() { return new CapturingNotifier(); }
}
