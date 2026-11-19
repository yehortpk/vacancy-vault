package com.github.yehortpk.dashboard.repositories;

import com.github.yehortpk.dashboard.models.vacancy.Vacancy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VacancyRepository extends JpaRepository<Vacancy, Integer> {
    /** An empty query or a false flag drops that condition. */
    @Query("""
            SELECT v FROM Vacancy v
            WHERE (:query = '' OR LOWER(v.title) LIKE LOWER(CONCAT('%', :query, '%')))
              AND (:remoteRequired = FALSE OR v.isRemote = TRUE)
              AND (:salaryRequired = FALSE OR v.minSalary IS NOT NULL OR v.maxSalary IS NOT NULL)
            """)
    Page<Vacancy> search(@Param("query") String query,
                         @Param("remoteRequired") boolean remoteRequired,
                         @Param("salaryRequired") boolean salaryRequired,
                         Pageable pageable);

    @Query("SELECT v FROM Vacancy v WHERE v.vacancyID = :id")
    Optional<Vacancy> findVacancyById(@Param("id") int id);

    @Query("SELECT count(v) > 0 FROM Vacancy v WHERE v.link = :link")
    boolean existsByLink(@Param("link") String link);

    @Modifying
    @Query("DELETE FROM Vacancy v WHERE v.link = :link")
    int deleteByLink(@Param("link") String link);
}
