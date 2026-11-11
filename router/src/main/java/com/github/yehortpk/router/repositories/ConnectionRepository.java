package com.github.yehortpk.router.repositories;

import com.github.yehortpk.router.models.connection.Connection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConnectionRepository extends JpaRepository<Connection, Long> {
    @Query("select c from Connection c where c.user.id = :userId order by c.linkedAt, c.id")
    List<Connection> findByUserId(@Param("userId") long userId);

    /**
     * Scoped by owner on purpose: every request naming a connection id goes through here, and
     * an id that belongs to someone else comes back empty, reported as a 404.
     */
    @Query("select c from Connection c where c.id = :id and c.user.id = :userId")
    Optional<Connection> findByIdAndUserId(@Param("id") long id, @Param("userId") long userId);
}
