package com.github.yehortpk.dashboard.models.vacancy;

import com.github.yehortpk.dashboard.models.company.CompanyShortInfoDTO;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Set;

@Data
public class VacancyCompanyDTO {
    /** Addresses the vacancy's own page; the description is left out of listings and served there. */
    private int id;
    private String title;
    private Integer minSalary;
    private Integer maxSalary;
    private String salaryCurrency;
    private String salaryPeriod;
    private String URL;
    private CompanyShortInfoDTO company;
    /** With the dashboard's offset, so the dashboard shows the time in the viewer's zone. */
    private OffsetDateTime crawledAt;
    /** Serialized as {@code remote}, after the Lombok accessor pair {@code isRemote()/setRemote()}. */
    private boolean isRemote;
    /** ISO 3166-1 alpha-2 codes; empty when the source does not say. */
    private Set<String> countries;
}
