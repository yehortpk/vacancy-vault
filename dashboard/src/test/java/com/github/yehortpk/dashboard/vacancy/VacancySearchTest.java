package com.github.yehortpk.dashboard.vacancy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yehortpk.dashboard.PostgresContainerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The {@code query}, {@code remote}, {@code withSalary} and {@code country} conditions on the
 * public vacancy listing, its order, and the countries it can be narrowed to.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VacancySearchTest extends PostgresContainerTest {
    private static final int COMPANY_ID = 1;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private void clearData() {
        // Other classes count rows in the shared container
        jdbcTemplate.update("DELETE FROM vacancy");
    }

    @BeforeEach
    void setUp() {
        clearData();
        insertVacancy("Java Office No Salary", false, null, null);
        insertVacancy("Java Remote No Salary", true, null, null);
        insertVacancy("Java Office Min Salary", false, 1000, null);
        insertVacancy("Java Remote Max Salary", true, null, 3000);
        insertVacancy("Python Remote Range", true, 2000, 4000);
        addCountries("Java Office No Salary", "pl");
        addCountries("Java Remote No Salary", "pl", "ua");
        addCountries("Python Remote Range", "ua");
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    private void insertVacancy(String title, boolean isRemote, Integer minSalary, Integer maxSalary) {
        jdbcTemplate.update("INSERT INTO vacancy (is_remote, link, max_salary, min_salary, crawled_at, title,"
                        + " company_id, company_title) VALUES (?, ?, ?, ?, now(), ?, ?, 'Acme')",
                isRemote, "https://acme.example/" + title.replace(' ', '-'), maxSalary, minSalary, title, COMPANY_ID);
    }

    private void addCountries(String title, String... countries) {
        for (String country : countries) {
            jdbcTemplate.update("INSERT INTO vacancy_country (vacancy_id, country)"
                    + " SELECT vacancy_id, ? FROM vacancy WHERE title = ?", country, title);
        }
    }

    private List<String> titlesOf(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request.param("page", "0").param("pageSize", "100"))
                .andExpect(status().isOk())
                .andReturn();
        List<String> titles = new ArrayList<>();
        objectMapper.readTree(result.getResponse().getContentAsString())
                .get("vacancies").forEach(vacancy -> titles.add(vacancy.get("title").asText()));
        return titles;
    }

    private void markCrawledHoursAgo(String title, int hours) {
        jdbcTemplate.update("UPDATE vacancy SET crawled_at = now() - make_interval(hours => ?) WHERE title = ?",
                hours, title);
    }

    @Test
    void GetVacancies_DifferentParseTimes_ShouldListNewestFirst() throws Exception {
        markCrawledHoursAgo("Java Office No Salary", 1);
        markCrawledHoursAgo("Java Remote No Salary", 3);
        markCrawledHoursAgo("Java Office Min Salary", 2);

        // "Java Remote Max Salary" keeps the parse time of now()
        assertThat(titlesOf(get("/vacancy").param("query", "Java"))).containsExactly(
                "Java Remote Max Salary", "Java Office No Salary", "Java Office Min Salary", "Java Remote No Salary");
    }

    @Test
    void GetVacancies_NoConditions_ShouldReturnEveryVacancy() throws Exception {
        assertThat(titlesOf(get("/vacancy"))).hasSize(5);
    }

    @Test
    void GetVacancies_QueryInDifferentCase_ShouldMatchTitlesIgnoringCase() throws Exception {
        assertThat(titlesOf(get("/vacancy").param("query", "jAVA rEMOTE")))
                .containsExactlyInAnyOrder("Java Remote No Salary", "Java Remote Max Salary");
    }

    @Test
    void GetVacancies_QueryWithSurroundingSpaces_ShouldIgnoreTheSpaces() throws Exception {
        assertThat(titlesOf(get("/vacancy").param("query", "  python "))).containsExactly("Python Remote Range");
    }

    @Test
    void GetVacancies_BlankQuery_ShouldReturnEveryVacancy() throws Exception {
        assertThat(titlesOf(get("/vacancy").param("query", "   "))).hasSize(5);
    }

    @Test
    void GetVacancies_RemoteRequired_ShouldReturnOnlyRemoteVacancies() throws Exception {
        assertThat(titlesOf(get("/vacancy").param("remote", "true")))
                .containsExactlyInAnyOrder("Java Remote Max Salary", "Java Remote No Salary", "Python Remote Range");
    }

    @Test
    void GetVacancies_SalaryRequired_ShouldReturnVacanciesWithAnySalaryBound() throws Exception {
        assertThat(titlesOf(get("/vacancy").param("withSalary", "true")))
                .containsExactlyInAnyOrder("Java Office Min Salary", "Java Remote Max Salary", "Python Remote Range");
    }

    @Test
    void GetVacancies_AllConditions_ShouldReturnVacanciesMatchingEveryCondition() throws Exception {
        assertThat(titlesOf(get("/vacancy").param("query", "Java")
                .param("remote", "true").param("withSalary", "true")))
                .containsExactlyInAnyOrder("Java Remote Max Salary");
    }

    @Test
    void GetVacancies_Country_ShouldReturnVacanciesOfferedInIt() throws Exception {
        assertThat(titlesOf(get("/vacancy").param("country", "ua")))
                .containsExactlyInAnyOrder("Java Remote No Salary", "Python Remote Range");
    }

    @Test
    void GetVacancies_CountryInUpperCaseWithSpaces_ShouldMatchTheCode() throws Exception {
        assertThat(titlesOf(get("/vacancy").param("country", " PL ")))
                .containsExactlyInAnyOrder("Java Office No Salary", "Java Remote No Salary");
    }

    @Test
    void GetVacancies_BlankCountry_ShouldReturnEveryVacancy() throws Exception {
        assertThat(titlesOf(get("/vacancy").param("country", ""))).hasSize(5);
    }

    @Test
    void GetVacancies_CountryWithOtherConditions_ShouldReturnVacanciesMatchingEveryCondition() throws Exception {
        assertThat(titlesOf(get("/vacancy").param("country", "pl").param("remote", "true")))
                .containsExactly("Java Remote No Salary");
    }

    @Test
    void GetVacancies_VacancyInSeveralCountries_ShouldListItOnce() throws Exception {
        assertThat(titlesOf(get("/vacancy").param("query", "Java Remote No Salary"))).hasSize(1);
    }

    @Test
    void GetVacancies_Listing_ShouldIncludeEachVacancysCountries() throws Exception {
        MvcResult result = mockMvc.perform(get("/vacancy").param("page", "0").param("query", "Java Remote No Salary"))
                .andExpect(status().isOk())
                .andReturn();
        List<String> countries = new ArrayList<>();
        objectMapper.readTree(result.getResponse().getContentAsString())
                .get("vacancies").get(0).get("countries").forEach(country -> countries.add(country.asText()));
        assertThat(countries).containsExactlyInAnyOrder("pl", "ua");
    }

    @Test
    void GetCountries_StoredVacancies_ShouldReturnTheirDistinctCountriesSorted() throws Exception {
        MvcResult result = mockMvc.perform(get("/vacancy/countries"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readValue(result.getResponse().getContentAsString(), String[].class))
                .containsExactly("pl", "ua");
    }

    @Test
    void GetVacancies_FrontendOrigin_ShouldAllowTheCrossOriginRead() throws Exception {
        mockMvc.perform(get("/vacancy").param("page", "0").header("Origin", "http://vv-frontend:4200"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin"))
                        .isEqualTo("http://vv-frontend:4200"));
    }
}
