package com.github.yehortpk.router.models.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A native account. Identity used to be the Telegram chat id; it is now a row here, and
 * messengers are linked to it as delivery destinations.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class AppUser {
    @EqualsAndHashCode.Include
    @ToString.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** BCrypt of the API key's secret half; null until a key is generated. */
    @Column(name = "api_key_hash", length = 100)
    private String apiKeyHash;

    /**
     * Plaintext lookup half of the API key. Stored unhashed so resolving a pasted key is a
     * single indexed read rather than a BCrypt comparison per account.
     */
    @Column(name = "api_key_prefix", length = 16)
    private String apiKeyPrefix;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private boolean enabled;
}
