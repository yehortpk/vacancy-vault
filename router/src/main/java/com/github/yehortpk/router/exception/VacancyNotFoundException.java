package com.github.yehortpk.router.exception;

public class VacancyNotFoundException extends RuntimeException {
    public VacancyNotFoundException() {
        super("Vacancy not found");
    }
}
