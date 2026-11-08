package com.greenbasket.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Development default: there is no SMTP server yet, so the token is written to the server log. */
@Component
public class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingPasswordResetNotifier.class);

    @Override
    public void send(String username, String email, String token) {
        LOG.warn("PASSWORD RESET for '{}' <{}>: token = {}  (dev mode: no mail server configured)", username, email, token);
    }
}
