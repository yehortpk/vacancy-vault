package com.github.yehortpk.router.models.vacancy;

/**
 * A vacancy the parser dropped for good, as it publishes it to the {@code vacancies} topic, keyed
 * by its link. The router notifies about none of them, but must still be able to read them.
 */
public record RemovedVacancyDTO(String link) {
}
