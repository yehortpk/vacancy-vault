package com.github.yehortpk.router.repositories;

import com.github.yehortpk.router.models.vacancy.Vacancy;
import lombok.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VacancyRepository extends JpaRepository<Vacancy, Long> {
    @Query("SELECT v.vacancyID FROM Vacancy v")
    List<Long> findAllIds();

    @EntityGraph(type = EntityGraph.EntityGraphType.FETCH, attributePaths = {"company"})
    @NonNull
    List<Vacancy> findAll();

    Page<Vacancy> findByTitleContaining(String title, Pageable pageable);

    boolean existsByLink(String link);
}
