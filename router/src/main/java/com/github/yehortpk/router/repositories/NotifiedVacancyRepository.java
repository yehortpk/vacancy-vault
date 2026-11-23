package com.github.yehortpk.router.repositories;

import com.github.yehortpk.router.models.vacancy.NotifiedVacancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface NotifiedVacancyRepository extends JpaRepository<NotifiedVacancy, String> {
    /**
     * Records the link unless it is recorded already, in one statement, so two deliveries of
     * the same vacancy cannot both come out as the first.
     *
     * @return 1 if the link was new, 0 if it was already there
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO {h-schema}notified_vacancy (link, notified_at) VALUES (:link, :notifiedAt)
            ON CONFLICT (link) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("link") String link, @Param("notifiedAt") LocalDateTime notifiedAt);
}
