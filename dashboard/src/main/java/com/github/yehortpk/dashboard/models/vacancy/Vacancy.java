package com.github.yehortpk.dashboard.models.vacancy;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@Entity
@Table(name = "vacancy")
@Builder
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Vacancy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vacancy_id")
    private int vacancyID;
    @EqualsAndHashCode.Include
    @Column(unique = true, nullable = false)
    private String link;
    @ToString.Include
    private String title;
    private Integer minSalary;
    private Integer maxSalary;
    private String salaryCurrency;
    private String salaryPeriod;
    private LocalDateTime crawledAt;
    /**
     * Whether remote type of work available for the vacancy
     */
    private boolean isRemote;
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * The company as the crawler last published it. Plain columns: companies are the crawler's,
     * and every vacancy of one company carries the same title and URL.
     */
    @Column(nullable = false)
    private int companyId;
    private String companyTitle;
    private String companyUrl;
}
