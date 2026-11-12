package com.github.yehortpk.router.providers.telegram;

import com.github.yehortpk.router.models.connection.Connection;
import jakarta.persistence.*;
import lombok.*;

/**
 * Telegram's details of a connection: the chat that receives the notifications.
 *
 * <p>Shares its primary key with the {@link Connection} ({@code @MapsId}) and is mapped in this
 * direction only, so the core never reaches into this table. {@code provider_id} is left
 * unmapped: its default is the only value the table's CHECK allows.
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
    private Long connectionId;

    @MapsId
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id")
    @ToString.Exclude
    private Connection connection;

    /** Negative for groups. One chat belongs to at most one account, by the table's UNIQUE. */
    @Column(name = "chat_id", nullable = false, unique = true)
    private long chatId;
}
