package com.github.yehortpk.router.models.connection;

import com.github.yehortpk.router.models.provider.Provider;
import com.github.yehortpk.router.models.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One attempt to link a destination, identified by a one-time token.
 *
 * <p>Only the token's SHA-256 is stored. The session is pending until a provider reports the
 * token back with a destination, which consumes it; after {@code expiresAt} it can no longer
 * be consumed.
 */
@Entity
@Table(name = "link_session")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class LinkSession {
    @EqualsAndHashCode.Include
    @ToString.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    @ToString.Exclude
    private AppUser user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "provider_id", nullable = false, updatable = false)
    @ToString.Exclude
    private Provider provider;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    @ToString.Exclude
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    /** What the session produced; null while pending, and again once that connection is unlinked. */
    @ManyToOne
    @JoinColumn(name = "connection_id")
    @ToString.Exclude
    private Connection connection;
}
