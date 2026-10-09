package com.college.timetable.config;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.college.timetable.entity.AppUser;
import com.college.timetable.entity.Role;
import com.college.timetable.repository.AppUserRepository;

/**
 * Creates the first administrator account.
 *
 * <p>Public registration is disabled, so somebody has to bootstrap the first login. This runner
 * does it exactly once and only when both environment variables are present:
 *
 * <pre>
 * BOOTSTRAP_ADMIN_USERNAME=admin
 * BOOTSTRAP_ADMIN_PASSWORD=&lt;at least 12 characters&gt;
 * </pre>
 *
 * <p>The password is read from the environment and is never logged. Once the account exists the
 * variables can be removed from the environment.
 */
@Component
@Order(0)
@ConditionalOnProperty(name = "college.bootstrap.enabled", havingValue = "true")
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CollegeProperties properties;
    private final String username;
    private final String password;

    public BootstrapAdminInitializer(AppUserRepository userRepository,
                                     PasswordEncoder passwordEncoder,
                                     CollegeProperties properties,
                                     org.springframework.core.env.Environment environment) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.username = environment.getProperty("college.bootstrap.username");
        this.password = environment.getProperty("college.bootstrap.password");
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.error("Bootstrap is enabled but BOOTSTRAP_ADMIN_USERNAME or BOOTSTRAP_ADMIN_PASSWORD "
                    + "is missing. No administrator was created.");
            return;
        }
        if (userRepository.existsByUsernameIgnoreCase(username.trim())) {
            log.info("Administrator '{}' already exists; bootstrap skipped.", username.trim());
            return;
        }
        if (password.length() < 12) {
            log.error("BOOTSTRAP_ADMIN_PASSWORD must be at least 12 characters. No administrator was created.");
            return;
        }

        AppUser admin = new AppUser();
        admin.setUsername(username.trim());
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setDisplayName(properties.getName() + " administrator");
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        admin.setPasswordChangedAt(Instant.now());
        userRepository.save(admin);

        log.info("Created the initial administrator account '{}'. "
                + "Remove BOOTSTRAP_ADMIN_PASSWORD from the environment now.", admin.getUsername());
    }
}