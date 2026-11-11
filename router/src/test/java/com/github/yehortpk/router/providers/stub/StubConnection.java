package com.github.yehortpk.router.providers.stub;

import com.github.yehortpk.router.models.connection.Connection;
import jakarta.persistence.*;
import lombok.*;

/**
 * The stub provider's details of a connection, mapped the way a real provider maps its own:
 * shares its primary key with the {@link Connection} ({@code @MapsId}), in this direction
 * only, and leaves {@code provider_id} unmapped since its default is the only value allowed.
 */
@Entity
@Table(name = "stub_connection")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class StubConnection {
    @EqualsAndHashCode.Include
    @ToString.Include
    @Id
    private Long connectionId;

    @MapsId
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id")
    @ToString.Exclude
    private Connection connection;

    @Column(nullable = false, unique = true)
    private String address;
}
