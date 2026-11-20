package com.github.yehortpk.parser.repositories;

import com.github.yehortpk.parser.models.company.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CompanyRepository extends JpaRepository<Company, Integer> {
    /**
     * The companies switched on for parsing, with the request data, headers and cookies their
     * crawlers need fetched in the same query.
     */
    @Query("""
            select distinct c from Company c
            left join fetch c.companyData
            left join fetch c.companyHeaders
            left join fetch c.companyCookies
            where c.isParsingEnabled = true
            order by c.companyId
            """)
    List<Company> findParsingEnabled();
}
