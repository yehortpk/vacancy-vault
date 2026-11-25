package com.github.yehortpk.parser.models;

/**
 * A vacancy dropped for good, as it is published to the {@code vacancies} topic, keyed by its link.
 */
public record RemovedVacancyDTO(String link) implements VacancyMessage {
}
