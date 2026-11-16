package com.github.yehortpk.router.models.vacancy;

import com.github.yehortpk.router.models.company.Company;
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
    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;
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
    private LocalDateTime parsedAt;

    /**
     * Outdated vacancy deletion timeout
     */
    private LocalDateTime deleteAt;
    /**
     * Whether remote type of work available for the vacancy
     */
    private boolean isRemote;
    @Column(columnDefinition = "TEXT")
    private String description;
}
