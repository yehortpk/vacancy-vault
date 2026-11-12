package com.github.yehortpk.router.models.filter;

import com.github.yehortpk.router.models.connection.Connection;
import com.github.yehortpk.router.models.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A vacancy filter an account authors in the web app.
 *
 * <p>It notifies the connections it is bound to. A filter bound to none is a draft: it is kept
 * and listed, but takes no part in matching new vacancies.
 */
@Entity
@Table(name = "filter")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Filter {
    @EqualsAndHashCode.Include
    @ToString.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    /** Lazy, since matching loads every active filter and never needs its owner. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    @ToString.Exclude
    private AppUser user;

    @Column(nullable = false)
    private String title;

    /** The expression, in the syntax {@code FilterParser} reads. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String filter;

    /**
     * Where a match is delivered. Both foreign keys of {@code filter_connection} cascade, so
     * unlinking a connection drops it from here without touching the filter.
     */
    @ManyToMany
    @JoinTable(
            name = "filter_connection",
            joinColumns = @JoinColumn(name = "filter_id"),
            inverseJoinColumns = @JoinColumn(name = "connection_id"))
    @ToString.Exclude
    private Set<Connection> connections = new LinkedHashSet<>();
}
