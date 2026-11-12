package com.github.yehortpk.router.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilterParserTest {
    @Test
    void IsVacancyApplicable_AllRulesSatisfied_ShouldReturnTrue() {
        FilterParser parser = new FilterParser("Google, (developer|engineer) software -senior -lead");

        assertThat(parser.isVacancyApplicable("Google", "Software Engineer")).isTrue();
    }

    @Test
    void IsVacancyApplicable_NegativeWordPresent_ShouldReturnFalse() {
        assertThat(new FilterParser("java -senior").isVacancyApplicable("Acme", "Senior Java Developer")).isFalse();
    }

    @Test
    void IsVacancyApplicable_OtherCompany_ShouldReturnFalse() {
        assertThat(new FilterParser("Google, java").isVacancyApplicable("Amazon", "Java Developer")).isFalse();
    }

    @Test
    void IsVacancyApplicable_CapitalizedWords_ShouldMatchCaseInsensitively() {
        // The vacancy is lowercased before matching, so capitalized words never matched
        FilterParser parser = new FilterParser("(Backend|Fullstack) Java -Senior");

        assertThat(parser.isVacancyApplicable("Acme", "Java Backend Developer")).isTrue();
        assertThat(parser.isVacancyApplicable("Acme", "Senior Java Backend Developer")).isFalse();
    }

    @Test
    void IsVacancyApplicable_DoubleSpaceBetweenNegativeWords_ShouldStillMatch() {
        // A blank negative word is contained in every input and used to veto every vacancy
        assertThat(new FilterParser("java -senior  -lead").isVacancyApplicable("Acme", "Java Developer")).isTrue();
    }

    @Test
    void Constructor_SpaceAfterMinus_ShouldThrow() {
        assertThatThrownBy(() -> new FilterParser("java - senior")).isInstanceOf(RuntimeException.class);
    }
}
