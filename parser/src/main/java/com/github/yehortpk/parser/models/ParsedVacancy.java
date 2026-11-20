package com.github.yehortpk.parser.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A vacancy the parser has already published, i.e. its memory between runs. A run publishes
 * only the links that have no row here.
 */
@Entity
@Table(name = "parsed_vacancy")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ParsedVacancy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @EqualsAndHashCode.Include
    @Column(nullable = false, unique = true)
    private String link;

    @Column(name = "company_id", nullable = false)
    private int companyId;

    @Column(name = "parsed_at", nullable = false)
    private LocalDateTime parsedAt;

    /**
     * Set while the vacancy is missing from its site: when it passes, the vacancy is dropped
     * and reported as removed. Null for a vacancy that is still listed.
     */
    @Column(name = "delete_at")
    private LocalDateTime deleteAt;
}
