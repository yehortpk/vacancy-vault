package com.github.yehortpk.crawler.models;

import com.github.yehortpk.crawler.utils.SalaryPeriod;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO representing a vacancy
 */
@Getter
@Builder(toBuilder = true)
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class VacancyDTO implements Serializable {
    private int companyID;
    private String companyTitle;
    /**
     * Vacancy title
     */
    private String title;
    private Integer minSalary;
    private Integer maxSalary;
    /**
     * ISO 4217 currency code, e.g. UAH; null when the source does not say
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

    private LocalDateTime crawledAt;

    /**
     * Whether remote type of work available for the vacancy
     */
    private boolean isRemote;

    private String description;
}
