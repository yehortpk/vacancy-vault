package com.github.yehortpk.dashboard.models.vacancy;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * A new vacancy as the crawler publishes it to the {@code vacancies} topic, keyed by its link.
 * The company travels with it, so a consumer needs no company table of its own.
 */
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class NewVacancyDTO {
    @EqualsAndHashCode.Include
    private String link;
    private String title;
    private String description;
    private Integer minSalary;
    private Integer maxSalary;
    private String salaryCurrency;
    private String salaryPeriod;
    /** Serialized as {@code remote}, after the Lombok accessor pair {@code isRemote()/setRemote()}. */
    private boolean isRemote;
    /** ISO 3166-1 alpha-2 codes; empty when the source does not say. */
    @Builder.Default
    private Set<String> countries = Set.of();
    private LocalDateTime crawledAt;
    private Company company;

    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Company {
        private int id;
        private String title;
        /** The company's main page. */
        private String url;
    }
}
