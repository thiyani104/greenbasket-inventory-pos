package com.greenbasket.config;

import com.greenbasket.domain.Role;
import com.greenbasket.domain.User;
import com.greenbasket.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first manager on an empty database so there is always a way to sign in. */
@Component
@Order(1)
public class BootstrapAdmin implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public BootstrapAdmin(UserRepository users, PasswordEncoder encoder,
                          @Value("${app.bootstrap.admin-username:admin}") String username,
                          @Value("${app.bootstrap.admin-password:Admin@1234}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        User u = new User(username, "Store Administrator", Role.MANAGER, encoder.encode(password));
        u.setMustChangePassword(true);
        users.save(u);
        LOG.warn("Created first manager account '{}'. Sign in and change the password immediately.", username);
    }
}
