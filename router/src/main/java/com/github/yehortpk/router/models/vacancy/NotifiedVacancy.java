package com.github.yehortpk.router.models.vacancy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

/**
 * The link of a vacancy the router has already matched against the filters, so a redelivered
 * or replayed {@code vacancies} message does not notify every chat a second time.
 *
 * <p>The router stores no vacancies; this is all it keeps of one, and only for as long as the
 * topic could still replay it, see {@code NotifiedVacancySweeper}.
 */
@Entity
@Table(name = "notified_vacancy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class NotifiedVacancy {
    @EqualsAndHashCode.Include
    @Id
    private String link;

    @Column(name = "notified_at", nullable = false)
    private LocalDateTime notifiedAt;
}
