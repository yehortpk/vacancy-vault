package com.github.yehortpk.router.repositories;

import com.github.yehortpk.router.models.vacancy.Vacancy;
import com.github.yehortpk.router.models.vacancy.VacancySearchCriteria;
import lombok.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VacancyRepository extends JpaRepository<Vacancy, Long> {
    @Query("SELECT v.vacancyID FROM Vacancy v")
    List<Long> findAllIds();

    @EntityGraph(type = EntityGraph.EntityGraphType.FETCH, attributePaths = {"company"})
    @NonNull
    List<Vacancy> findAll();

    /** Mirrors {@link VacancySearchCriteria#isSatisfiedBy}; an empty query or a false flag drops that condition. */
    @Query("""
            SELECT v FROM Vacancy v
            WHERE (:query = '' OR v.title LIKE CONCAT('%', :query, '%'))
              AND (:remoteRequired = FALSE OR v.isRemote = TRUE)
              AND (:salaryRequired = FALSE OR v.minSalary IS NOT NULL OR v.maxSalary IS NOT NULL)
            """)
    Page<Vacancy> search(@Param("query") String query,
                         @Param("remoteRequired") boolean remoteRequired,
                         @Param("salaryRequired") boolean salaryRequired,
                         Pageable pageable);

    boolean existsByLink(String link);
}
