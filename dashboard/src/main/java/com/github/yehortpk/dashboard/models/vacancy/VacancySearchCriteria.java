package com.github.yehortpk.dashboard.models.vacancy;

import lombok.Value;

import java.util.Locale;

/**
 * Conditions a vacancy listing is narrowed by. Each condition is off unless asked for: an empty
 * {@code query} matches every title, an empty {@code country} every country, and a {@code false}
 * flag does not filter at all.
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
    /** ISO 3166-1 alpha-2 code, in any case, one of the vacancy's countries; empty for no country condition. */
    String country;

    public VacancySearchCriteria(String query, boolean remoteRequired, boolean salaryRequired, String country) {
        // Typed into a search box, so a stray space around the words is not part of the search
        this.query = query == null ? "" : query.strip();
        this.remoteRequired = remoteRequired;
        this.salaryRequired = salaryRequired;
        // Codes are stored lower-case
        this.country = country == null ? "" : country.strip().toLowerCase(Locale.ROOT);
    }
}
