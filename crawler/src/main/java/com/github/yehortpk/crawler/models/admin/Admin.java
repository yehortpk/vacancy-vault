package com.github.yehortpk.crawler.models.admin;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An operator of the crawler's admin panel. Not related to the router's user accounts in any
 * way: the two services share no identity and no secret.
 */
@Entity
@Table(name = "admin")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Admin {
    @EqualsAndHashCode.Include
    @ToString.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ToString.Include
    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
