package com.github.yehortpk.dashboard.exception;

public class VacancyNotFoundException extends RuntimeException {
    public VacancyNotFoundException() {
        super("Vacancy not found");
    }
}
