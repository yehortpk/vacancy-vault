package com.github.yehortpk.router.startup;

import com.github.yehortpk.router.models.user.AppUser;
import com.github.yehortpk.router.models.user.UserRole;
import com.github.yehortpk.router.repositories.UserRepository;
import com.github.yehortpk.router.services.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Creates the admin account from {@code ADMIN_EMAIL} and {@code ADMIN_PASSWORD} on startup.
 *
 * <p>Only ever creates: an existing account with that address is left exactly as it is.
 * Registration does not verify that an address belongs to whoever registered it, so
 * promoting an existing account would hand the parser room to anyone who registered the
 * admin's address first. For the same reason a changed {@code ADMIN_PASSWORD} does not
 * overwrite the stored one.
 *
 * <p>Both values are optional; with either one blank no admin is created, and an account
 * can still be promoted directly in the database.
 */
@Component
@Slf4j
public class AdminAccountInitializer implements ApplicationRunner {
    /** Same minimum as registration, see {@code RegisterRequestDTO}. */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AuthService authService;
    private final UserRepository userRepository;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAccountInitializer(AuthService authService,
                                   UserRepository userRepository,
                                   @Value("${admin.email:}") String adminEmail,
                                   @Value("${admin.password:}") String adminPassword) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        createAdminAccount();
    }

    /**
     * @throws IllegalStateException if the configured password is too short, which stops
     *                               the router from starting rather than leaving no admin
     */
    public void createAdminAccount() {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.info("ADMIN_EMAIL or ADMIN_PASSWORD is not set; no admin account is created");
            return;
        }
        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        String email = AuthService.normalizeEmail(adminEmail);
        Optional<AppUser> existing = userRepository.findByEmail(email);

        if (existing.isEmpty()) {
            authService.createAccount(email, adminPassword, UserRole.ADMIN);
            log.info("Created the admin account {}", email);
        } else if (existing.get().getRole() != UserRole.ADMIN) {
            log.warn("{} is already registered as a {}; it is not promoted to admin automatically. "
                    + "Promote it in the database if it is yours.", email, existing.get().getRole());
        } else {
            log.info("Admin account {} already exists; left unchanged", email);
        }
    }
}
