package com.github.yehortpk.router.models.provider;

import com.github.yehortpk.router.models.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One user's linked Telegram chat — the destination a notification is actually sent to.
 *
 * <p>Standalone by design: no superclass and no discriminator, so {@code chatId} is a typed
 * {@code long} with its own UNIQUE constraint. Each messenger gets its own entity with its
 * own fields rather than sharing an overloaded address column.
 */
@Entity
@Table(name = "telegram_connection")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class TelegramConnection {
    @EqualsAndHashCode.Include
    @ToString.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Column(name = "chat_id", nullable = false, unique = true)
    private long chatId;

    /** Display label shown in the account panel; the user can rename it. */
    @Column
    private String title;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;
}
