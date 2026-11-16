package com.github.yehortpk.parser.models;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO representing a vacancy
 */
@Getter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class VacancyDTO implements Serializable {
    private int companyID;
    private String companyTitle;
    private int vacancyID;
    /**
     * Vacancy title
     */
    private String title;
    private Integer minSalary;
    private Integer maxSalary;
    /**
     * Currency code, preferably one of {@link Currency}; null when the source does not say
     */
    private String salaryCurrency;
    /**
     * Payout period, preferably one of {@link SalaryPeriod}; null when the source does not say
     */
    private String salaryPeriod;
    /**
     * Full link to the vacancy
     */
    @EqualsAndHashCode.Include
    private String link;

    private LocalDateTime parsedAt;

    /**
     * Outdated data deletion timeout
     */
    private LocalDateTime deleteAt;

    /**
     * Whether remote type of work available for the vacancy
     */
    private boolean isRemote;

    private String description;
}
