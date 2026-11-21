package com.github.yehortpk.parser.startup;

import com.github.yehortpk.parser.models.admin.Admin;
import com.github.yehortpk.parser.repositories.AdminRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Creates the first admin of the panel from {@code PARSER_ADMIN_USERNAME} and
 * {@code PARSER_ADMIN_PASSWORD} on startup.
 *
 * <p>Only ever creates: an admin with that username is left exactly as it is, so a changed
 * {@code PARSER_ADMIN_PASSWORD} does not overwrite the stored one. There is no registration,
 * so this and the database are the only ways an admin comes to exist.
 *
 * <p>Both values are optional; with either one blank no admin is created.
 */
@Component
@Slf4j
public class AdminAccountInitializer implements ApplicationRunner {
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public AdminAccountInitializer(AdminRepository adminRepository,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${parser.admin.username:}") String adminUsername,
                                   @Value("${parser.admin.password:}") String adminPassword) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        createAdminAccount();
    }

    /**
     * @throws IllegalStateException if the configured password is too short, which stops
     *                               the parser from starting rather than leaving no admin
     */
    public void createAdminAccount() {
        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            log.info("PARSER_ADMIN_USERNAME or PARSER_ADMIN_PASSWORD is not set; no admin account is created");
            return;
        }
        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "PARSER_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        String username = adminUsername.strip();
        if (adminRepository.findByUsername(username).isPresent()) {
            log.info("Admin account {} already exists; left unchanged", username);
            return;
        }

        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setCreatedAt(LocalDateTime.now());
        adminRepository.save(admin);
        log.info("Created the admin account {}", username);
    }
}
