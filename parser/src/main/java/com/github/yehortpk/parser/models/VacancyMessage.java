package com.github.yehortpk.parser.models;

/**
 * A record of the {@code vacancies} topic, keyed by the vacancy's link. Both kinds share the
 * topic, so the changes of one link reach every consumer in the order they were published. The
 * kind travels in the {@code __TypeId__} header the JSON serializer writes.
 */
public sealed interface VacancyMessage permits NewVacancyDTO, RemovedVacancyDTO {
}
