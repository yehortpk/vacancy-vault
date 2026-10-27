package com.github.yehortpk.router.models.provider;

import jakarta.persistence.*;
import lombok.*;

/**
 * A messenger platform in the catalog. Holds no address — see {@link TelegramConnection}.
 */
@Entity
@Table(name = "provider")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Provider {
    @EqualsAndHashCode.Include
    @ToString.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 32)
    private ChannelProvider code;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private boolean enabled;
}
