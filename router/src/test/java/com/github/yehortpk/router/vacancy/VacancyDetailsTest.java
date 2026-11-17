package com.github.yehortpk.router.vacancy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yehortpk.router.PostgresContainerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A single vacancy's page: served by id, anonymously, with the description the listing leaves out.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VacancyDetailsTest extends PostgresContainerTest {
    private static final int COMPANY_ID = 1;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private void clearData() {
        jdbcTemplate.update("DELETE FROM vacancy");
        jdbcTemplate.update("DELETE FROM company");
    }

    @BeforeEach
    void setUp() {
        clearData();
        jdbcTemplate.update("INSERT INTO company (company_id, title, main_page_url, parsing_enabled)"
                + " VALUES (?, 'Acme', 'https://acme.example', false)", COMPANY_ID);
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    private int insertVacancy(String title, String description) {
        return jdbcTemplate.queryForObject("INSERT INTO vacancy (is_remote, link, max_salary, min_salary, parsed_at,"
                        + " title, company_id, description) VALUES (true, ?, 3000, 1000, now(), ?, ?, ?)"
                        + " RETURNING vacancy_id",
                Integer.class, "https://acme.example/" + title.replace(' ', '-'), title, COMPANY_ID, description);
    }

    private JsonNode readJson(String path) throws Exception {
        String body = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    void GetVacancy_ExistingVacancy_ShouldReturnItsDetails() throws Exception {
        int id = insertVacancy("Java Developer", "Build things.\nShip them.");

        JsonNode vacancy = readJson("/vacancy/" + id);

        assertThat(vacancy.get("id").asInt()).isEqualTo(id);
        assertThat(vacancy.get("title").asText()).isEqualTo("Java Developer");
        assertThat(vacancy.get("description").asText()).isEqualTo("Build things.\nShip them.");
        assertThat(vacancy.get("url").asText()).isEqualTo("https://acme.example/Java-Developer");
        assertThat(vacancy.get("remote").asBoolean()).isTrue();
        assertThat(vacancy.get("minSalary").asInt()).isEqualTo(1000);
        assertThat(vacancy.get("company").get("title").asText()).isEqualTo("Acme");
        assertThat(vacancy.get("company").get("company_url").asText()).isEqualTo("https://acme.example");
    }

    @Test
    void GetVacancy_SalaryCurrencyAndPeriod_ShouldReturnThemAsStored() throws Exception {
        int id = insertVacancy("Java Developer", null);
        jdbcTemplate.update("UPDATE vacancy SET salary_currency = 'USD', salary_period = 'MONTH' WHERE vacancy_id = ?", id);

        JsonNode vacancy = readJson("/vacancy/" + id);

        assertThat(vacancy.get("salaryCurrency").asText()).isEqualTo("USD");
        assertThat(vacancy.get("salaryPeriod").asText()).isEqualTo("MONTH");
    }

    @Test
    void GetVacancy_NoDescription_ShouldReturnNullDescription() throws Exception {
        int id = insertVacancy("Java Developer", null);

        assertThat(readJson("/vacancy/" + id).get("description").isNull()).isTrue();
    }

    @Test
    void GetVacancy_UnknownId_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/vacancy/999999")).andExpect(status().isNotFound());
    }

    @Test
    void GetVacancies_VacancyWithDescription_ShouldListIdWithoutDescription() throws Exception {
        int id = insertVacancy("Java Developer", "Build things.");

        JsonNode listed = readJson("/vacancy?page=0").get("vacancies").get(0);

        assertThat(listed.get("id").asInt()).isEqualTo(id);
        assertThat(listed.has("description")).isFalse();
    }
}
