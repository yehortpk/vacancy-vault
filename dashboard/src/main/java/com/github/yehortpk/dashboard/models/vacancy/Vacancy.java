package com.github.yehortpk.dashboard.models.vacancy;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

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
    /**
     * ISO 3166-1 alpha-2 codes of the countries the vacancy is offered in; empty when the source
     * does not say. Loaded with the vacancy, batched so a listing page costs one extra query.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "vacancy_country", joinColumns = @JoinColumn(name = "vacancy_id"))
    @Column(name = "country", nullable = false, length = 2)
    @BatchSize(size = 100)
    @Builder.Default
    private Set<String> countries = new HashSet<>();
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
