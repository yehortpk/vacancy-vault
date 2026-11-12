package com.github.yehortpk.router.models.connection;

import com.github.yehortpk.router.models.provider.Provider;
import com.github.yehortpk.router.models.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One linked destination of one account, whatever the messenger.
 *
 * <p>The core works only with this entity: listing, renaming, unlinking, ownership and filter
 * bindings. What the destination actually is lives in the provider's own details
 * entity (e.g. {@code TelegramConnection}), which points here and which only that provider's
 * code reads. There is deliberately no mapping in this direction, so the core cannot reach
 * into a provider's table by accident.
 */
@Entity
@Table(name = "connection")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Connection {
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

    /** Display label shown in the account panel; the user can rename it. */
    @Column
    private String title;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;
}
