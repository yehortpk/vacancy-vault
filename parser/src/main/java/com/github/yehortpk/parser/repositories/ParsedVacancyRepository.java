package com.github.yehortpk.parser.repositories;

import com.github.yehortpk.parser.models.ParsedVacancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface ParsedVacancyRepository extends JpaRepository<ParsedVacancy, Long> {
    @Query("select v from ParsedVacancy v where v.companyId = :companyId")
    List<ParsedVacancy> findByCompanyId(@Param("companyId") int companyId);

    /** Those of the given links that are already published, whichever company they belong to. */
    @Query("select v.link from ParsedVacancy v where v.link in :links")
    Set<String> findPublishedLinks(@Param("links") Collection<String> links);

    /** Sets or, with {@code null}, clears the deletion marker of these vacancies. */
    @Modifying
    @Query("update ParsedVacancy v set v.deleteAt = :deleteAt where v.link in :links")
    int updateDeleteAt(@Param("links") Collection<String> links, @Param("deleteAt") LocalDateTime deleteAt);

    @Modifying
    @Query("delete from ParsedVacancy v where v.link in :links")
    int deleteByLinks(@Param("links") Collection<String> links);
}
