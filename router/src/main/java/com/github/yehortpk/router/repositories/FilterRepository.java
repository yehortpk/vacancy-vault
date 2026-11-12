package com.github.yehortpk.router.repositories;

import com.github.yehortpk.router.models.filter.Filter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FilterRepository extends JpaRepository<Filter, Long> {
    @Query("select f from Filter f where f.user.id = :userId order by f.id")
    List<Filter> findByUserId(@Param("userId") long userId);

    /**
     * Scoped by owner on purpose: every request naming a filter id goes through here, and an
     * id that belongs to someone else comes back empty, reported as a 404.
     */
    @Query("select f from Filter f where f.id = :id and f.user.id = :userId")
    Optional<Filter> findByIdAndUserId(@Param("id") long id, @Param("userId") long userId);

    /**
     * The filters that take part in matching: those bound to at least one connection, which
     * the inner join fetches in the same query. Drafts are left out by the join itself.
     */
    @Query("select distinct f from Filter f join fetch f.connections")
    List<Filter> findActive();

    @Query("select count(f) from Filter f where f.user.id = :userId")
    long countByUserId(@Param("userId") long userId);

    /** The filters that deliver to this connection. */
    @Query("select count(f) from Filter f join f.connections c where c.id = :connectionId")
    long countByConnectionId(@Param("connectionId") long connectionId);
}
