package com.github.yehortpk.router.startup;

import com.github.yehortpk.router.PostgresContainerTest;
import com.github.yehortpk.router.models.user.AppUser;
import com.github.yehortpk.router.models.user.UserRole;
import com.github.yehortpk.router.repositories.UserRepository;
import com.github.yehortpk.router.services.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The startup admin account against the migrated schema. Each test builds its own
 * initializer with the values under test; the one Spring runs at startup has none
 * configured and so creates nothing.
 */
@SpringBootTest
class AdminAccountInitializerTest extends PostgresContainerTest {
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "admin-password-123";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearAccounts() {
        userRepository.deleteAll();
    }

    private AdminAccountInitializer initializer(String email, String password) {
        return new AdminAccountInitializer(authService, userRepository, email, password);
    }

    private AppUser findAccount(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    @Test
    void CreateAdminAccount_NothingConfigured_ShouldCreateNoAccount() {
        initializer("", "").createAdminAccount();

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void CreateAdminAccount_NewEmail_ShouldCreateAnAdminWithTheConfiguredPassword() {
        initializer(ADMIN_EMAIL, ADMIN_PASSWORD).createAdminAccount();

        AppUser admin = findAccount(ADMIN_EMAIL);
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();
    }

    @Test
    void CreateAdminAccount_EmailInMixedCase_ShouldStoreItNormalized() {
        // Login normalizes the address, so a stored mixed-case one could never log in
        initializer("  Admin@Example.COM ", ADMIN_PASSWORD).createAdminAccount();

        assertThat(findAccount(ADMIN_EMAIL).getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void CreateAdminAccount_EmailAlreadyRegisteredAsUser_ShouldNotPromoteIt() {
        // Registration does not prove ownership of the address, so whoever registered it
        // first must not inherit the parser room
        authService.createAccount(ADMIN_EMAIL, "someone-elses-password", UserRole.USER);

        initializer(ADMIN_EMAIL, ADMIN_PASSWORD).createAdminAccount();

        AppUser existing = findAccount(ADMIN_EMAIL);
        assertThat(existing.getRole()).isEqualTo(UserRole.USER);
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, existing.getPasswordHash())).isFalse();
    }

    @Test
    void CreateAdminAccount_AdminAlreadyExists_ShouldKeepTheStoredPassword() {
        initializer(ADMIN_EMAIL, ADMIN_PASSWORD).createAdminAccount();

        initializer(ADMIN_EMAIL, "a-changed-password-456").createAdminAccount();

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, findAccount(ADMIN_EMAIL).getPasswordHash())).isTrue();
    }

    @Test
    void CreateAdminAccount_PasswordShorterThanMinimum_ShouldThrow() {
        assertThatThrownBy(() -> initializer(ADMIN_EMAIL, "short").createAdminAccount())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 8");

        assertThat(userRepository.count()).isZero();
    }
}
