package com.github.yehortpk.crawler.company;

import com.github.yehortpk.crawler.models.CompanyDTO;

import java.util.List;

/**
 * Where a crawling run takes its companies from. {@link DatabaseCompanySource} reads the {@code company}
 * table, unless the application defines its own {@code CompanySource} bean.
 */
public interface CompanySource {
    /**
     * Returns the companies to crawl in this run
     * @return companies list
     */
    List<CompanyDTO> getCompanies();
}
