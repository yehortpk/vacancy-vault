package com.github.yehortpk.crawler.repositories;

import com.github.yehortpk.crawler.models.CrawledVacancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface CrawledVacancyRepository extends JpaRepository<CrawledVacancy, Long> {
    @Query("select v from CrawledVacancy v where v.companyId = :companyId")
    List<CrawledVacancy> findByCompanyId(@Param("companyId") int companyId);

    /** Those of the given links that are already published, whichever company they belong to. */
    @Query("select v.link from CrawledVacancy v where v.link in :links")
    Set<String> findPublishedLinks(@Param("links") Collection<String> links);

    /** Sets or, with {@code null}, clears the deletion marker of these vacancies. */
    /** Links whose grace period ended before the given moment; a listed vacancy has none. */
    @Query("select v.link from CrawledVacancy v where v.deleteAt < :moment")
    List<String> findExpiredLinks(@Param("moment") LocalDateTime moment);

    @Modifying
    @Query("update CrawledVacancy v set v.deleteAt = :deleteAt where v.link in :links")
    int updateDeleteAt(@Param("links") Collection<String> links, @Param("deleteAt") LocalDateTime deleteAt);

    @Modifying
    @Query("delete from CrawledVacancy v where v.link in :links")
    int deleteByLinks(@Param("links") Collection<String> links);
}
