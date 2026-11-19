package com.github.yehortpk.dashboard.models.vacancy;

import lombok.Value;

/**
 * Conditions a vacancy listing is narrowed by. Each condition is off unless asked for: an empty
 * {@code query} matches every title, and a {@code false} flag does not filter at all.
 *
 * <p>The database applies these through {@code VacancyRepository.search}.
 */
@Value
public class VacancySearchCriteria {
    /** Case-insensitive substring of the vacancy title; empty for no title condition. */
    String query;
    /** Only vacancies the source marked as remote. */
    boolean remoteRequired;
    /** Only vacancies that publish at least one salary bound (a missing bound is null). */
    boolean salaryRequired;

    public VacancySearchCriteria(String query, boolean remoteRequired, boolean salaryRequired) {
        // Typed into a search box, so a stray space around the words is not part of the search
        this.query = query == null ? "" : query.strip();
        this.remoteRequired = remoteRequired;
        this.salaryRequired = salaryRequired;
    }
}
