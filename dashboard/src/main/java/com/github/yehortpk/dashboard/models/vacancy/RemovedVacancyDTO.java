package com.github.yehortpk.dashboard.models.vacancy;

/**
 * A vacancy the crawler dropped for good, as it publishes it to the {@code vacancies} topic, keyed
 * by its link.
 */
public record RemovedVacancyDTO(String link) {
}
