package com.github.yehortpk.parser.startup;

import com.github.yehortpk.parser.PostgresContainerTest;
import com.github.yehortpk.parser.models.admin.Admin;
import com.github.yehortpk.parser.repositories.AdminRepository;
import org.junit.jupiter.api.AfterEach;
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
    private static final String ADMIN_USERNAME = "operator";
    private static final String ADMIN_PASSWORD = "admin-password-123";

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearBefore() {
        adminRepository.deleteAll();
    }

    @AfterEach
    void clearAfter() {
        adminRepository.deleteAll();
    }

    private AdminAccountInitializer createInitializer(String username, String password) {
        return new AdminAccountInitializer(adminRepository, passwordEncoder, username, password);
    }

    private Admin findAdmin(String username) {
        return adminRepository.findByUsername(username).orElseThrow();
    }

    @Test
    void CreateAdminAccount_NothingConfigured_ShouldCreateNoAccount() {
        createInitializer("", "").createAdminAccount();

        assertThat(adminRepository.count()).isZero();
    }

    @Test
    void CreateAdminAccount_PasswordNotConfigured_ShouldCreateNoAccount() {
        createInitializer(ADMIN_USERNAME, "").createAdminAccount();

        assertThat(adminRepository.count()).isZero();
    }

    @Test
    void CreateAdminAccount_NewUsername_ShouldCreateAnAdminWithTheConfiguredPassword() {
        createInitializer(ADMIN_USERNAME, ADMIN_PASSWORD).createAdminAccount();

        Admin admin = findAdmin(ADMIN_USERNAME);
        assertThat(admin.getCreatedAt()).isNotNull();
        // Only ever a hash
        assertThat(admin.getPasswordHash()).isNotEqualTo(ADMIN_PASSWORD);
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();
    }

    @Test
    void CreateAdminAccount_UsernameWithSurroundingSpaces_ShouldStoreItStripped() {
        createInitializer("  " + ADMIN_USERNAME + " ", ADMIN_PASSWORD).createAdminAccount();

        assertThat(adminRepository.findByUsername(ADMIN_USERNAME)).isPresent();
    }

    @Test
    void CreateAdminAccount_AdminAlreadyExists_ShouldKeepTheStoredPassword() {
        createInitializer(ADMIN_USERNAME, ADMIN_PASSWORD).createAdminAccount();

        createInitializer(ADMIN_USERNAME, "a-changed-password-456").createAdminAccount();

        assertThat(adminRepository.count()).isEqualTo(1);
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, findAdmin(ADMIN_USERNAME).getPasswordHash())).isTrue();
    }

    @Test
    void CreateAdminAccount_PasswordShorterThanMinimum_ShouldThrow() {
        assertThatThrownBy(() -> createInitializer(ADMIN_USERNAME, "short").createAdminAccount())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 8");

        assertThat(adminRepository.count()).isZero();
    }
}
