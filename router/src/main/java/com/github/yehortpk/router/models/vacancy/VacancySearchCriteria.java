package com.github.yehortpk.router.models.vacancy;

import lombok.Value;

/**
 * Conditions a vacancy listing is narrowed by. Each condition is off unless asked for: an empty
 * {@code query} matches every title, and a {@code false} flag does not filter at all.
 *
 * <p>The database applies these through {@code VacancyRepository.search}; {@link #isSatisfiedBy}
 * is its in-memory counterpart for listings filtered outside the database, and the two must agree.
 */
@Value
public class VacancySearchCriteria {
    /** Case-sensitive substring of the vacancy title; empty for no title condition. */
    String query;
    /** Only vacancies the source marked as remote. */
    boolean remoteRequired;
    /** Only vacancies that publish at least one salary bound (a missing bound is stored as 0). */
    boolean salaryRequired;

    public VacancySearchCriteria(String query, boolean remoteRequired, boolean salaryRequired) {
        this.query = query == null ? "" : query;
        this.remoteRequired = remoteRequired;
        this.salaryRequired = salaryRequired;
    }

    public boolean isSatisfiedBy(Vacancy vacancy) {
        if (!query.isEmpty() && (vacancy.getTitle() == null || !vacancy.getTitle().contains(query))) {
            return false;
        }
        if (remoteRequired && !vacancy.isRemote()) {
            return false;
        }
        return !salaryRequired || vacancy.getMinSalary() > 0 || vacancy.getMaxSalary() > 0;
    }
}
