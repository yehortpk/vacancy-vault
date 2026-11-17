package com.github.yehortpk.router.models.vacancy;

import com.github.yehortpk.router.models.company.CompanyShortInfoDTO;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * A single vacancy as its own page shows it: the listing's fields plus the description,
 * which listings leave out to stay light.
 */
@Data
public class VacancyDetailsDTO {
    private int id;
    private String title;
    private Integer minSalary;
    private Integer maxSalary;
    private String salaryCurrency;
    private String salaryPeriod;
    private String URL;
    private CompanyShortInfoDTO company;
    /** With the router's offset, so the page shows the time in the viewer's zone. */
    private OffsetDateTime parsedAt;
    /** Serialized as {@code remote}, after the Lombok accessor pair {@code isRemote()/setRemote()}. */
    private boolean isRemote;
    private String description;
}
