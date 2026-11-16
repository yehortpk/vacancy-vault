package com.github.yehortpk.parser.models;

/**
 * Suggested values for {@link VacancyDTO#getSalaryCurrency()}, by ISO 4217 code: the ten most
 * traded currencies plus UAH, which most of the parsed sources pay in. Pass {@code name()};
 * the field itself takes any code, so a currency missing here needs no change to be stored.
 */
public enum Currency {
    USD,
    EUR,
    JPY,
    GBP,
    CNY,
    AUD,
    CAD,
    CHF,
    HKD,
    SGD,
    UAH
}
