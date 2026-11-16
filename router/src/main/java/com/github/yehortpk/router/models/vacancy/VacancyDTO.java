package com.github.yehortpk.router.models.vacancy;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class VacancyDTO implements Serializable {
    private int companyID;
    private String companyTitle;
    private int vacancyID;
    private String title;
    private int minSalary;
    private int maxSalary;
    @EqualsAndHashCode.Include
    private String link;
    private LocalDateTime parsedAt;

    /**
     * Outdated vacancy deletion timeout
     */
    private LocalDateTime deleteAt;
    /**
     * Whether remote type of work available for the vacancy
     */
    private boolean isRemote;
    private String description;
}

