package com.github.yehortpk.router.models.provider;

import jakarta.persistence.*;
import lombok.*;

/**
 * A messenger platform in the catalog. Holds no destination; see {@code Connection}.
 *
 * <p>The id is not generated: each provider's migration inserts its row with a fixed id,
 * which its {@code ConnectionProvider} declares.
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
    private long id;

    @Column(nullable = false)
    private String title;

    /** What the user is told to do after starting a link; {@code {token}} marks the token. */
    @Column(name = "link_instructions", nullable = false, columnDefinition = "TEXT")
    private String linkInstructions;

    @Column(nullable = false)
    private boolean enabled;
}
