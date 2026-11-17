package com.github.yehortpk.router.models.vacancy;

import com.github.yehortpk.router.models.company.CompanyShortInfoDTO;
import lombok.Data;

import java.time.OffsetDateTime;

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
    /** With the router's offset, so the dashboard shows the time in the viewer's zone. */
    private OffsetDateTime parsedAt;
    /** Serialized as {@code remote}, after the Lombok accessor pair {@code isRemote()/setRemote()}. */
    private boolean isRemote;
}
