package com.github.yehortpk.router.repositories;

import com.github.yehortpk.router.models.connection.LinkSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LinkSessionRepository extends JpaRepository<LinkSession, Long> {
    /**
     * Locks the row, so two signals carrying the same token cannot both consume it: the second
     * waits, then sees the session already consumed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LinkSession s where s.tokenHash = :tokenHash")
    Optional<LinkSession> findForUpdate(@Param("tokenHash") String tokenHash);

    @Query("select s from LinkSession s where s.id = :id and s.user.id = :userId")
    Optional<LinkSession> findByIdAndUserId(@Param("id") long id, @Param("userId") long userId);

    /** Drops the user's unconsumed sessions for a provider, when a new one supersedes them. */
    @Modifying
    @Query("delete from LinkSession s where s.user.id = :userId and s.provider.id = :providerId"
            + " and s.consumedAt is null")
    void deleteUnconsumed(@Param("userId") long userId, @Param("providerId") long providerId);
}
