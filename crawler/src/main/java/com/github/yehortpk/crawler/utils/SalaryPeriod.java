package com.github.yehortpk.crawler.utils;

import com.github.yehortpk.crawler.models.VacancyDTO;

/**
 * Suggested values for {@link VacancyDTO#getSalaryPeriod()}. Pass {@code name()}; the field
 * itself takes any value.
 */
public enum SalaryPeriod {
    HOUR,
    WEEK,
    MONTH,
    YEAR
}
