package com.github.yehortpk.dashboard.company;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A company's page: the company as its newest stored vacancy carries it, and its vacancies only.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompanyTest extends PostgresContainerTest {
    private static final int ACME_ID = 1;
    private static final int GLOBEX_ID = 2;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private void clearData() {
        jdbcTemplate.update("DELETE FROM vacancy");
    }

    @BeforeEach
    void setUp() {
        clearData();
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    private void insertVacancy(String title, int companyId, String companyTitle, String crawledAt) {
        jdbcTemplate.update("INSERT INTO vacancy (is_remote, link, crawled_at, title,"
                        + " company_id, company_title, company_url)"
                        + " VALUES (false, ?, CAST(? AS TIMESTAMP), ?, ?, ?, 'https://company.example')",
                "https://company.example/" + title.replace(' ', '-'), crawledAt, title, companyId, companyTitle);
    }

    private JsonNode readJson(String path) throws Exception {
        String body = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private List<String> readTitles(JsonNode page) {
        List<String> titles = new ArrayList<>();
        page.get("vacancies").forEach(vacancy -> titles.add(vacancy.get("title").asText()));
        return titles;
    }

    @Test
    void GetCompany_StoredVacancies_ShouldReturnCompanyOfNewestVacancy() throws Exception {
        insertVacancy("Java Developer", ACME_ID, "Acme Old", "2026-01-01 10:00:00");
        insertVacancy("Go Developer", ACME_ID, "Acme", "2026-02-01 10:00:00");

        JsonNode company = readJson("/company/" + ACME_ID);

        assertThat(company.get("company_id").asInt()).isEqualTo(ACME_ID);
        assertThat(company.get("title").asText()).isEqualTo("Acme");
        assertThat(company.get("company_url").asText()).isEqualTo("https://company.example");
    }

    @Test
    void GetCompany_NoStoredVacancy_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/company/999999")).andExpect(status().isNotFound());
    }

    @Test
    void GetCompanyVacancies_SeveralCompanies_ShouldListOnlyThatCompanyNewestFirst() throws Exception {
        insertVacancy("Java Developer", ACME_ID, "Acme", "2026-01-01 10:00:00");
        insertVacancy("Go Developer", ACME_ID, "Acme", "2026-02-01 10:00:00");
        insertVacancy("Rust Developer", GLOBEX_ID, "Globex", "2026-03-01 10:00:00");

        JsonNode page = readJson("/company/" + ACME_ID + "/vacancies?page=0");

        assertThat(readTitles(page)).containsExactly("Go Developer", "Java Developer");
        assertThat(page.get("totalVacancies").asInt()).isEqualTo(2);
    }

    @Test
    void GetCompanyVacancies_PageSize_ShouldReturnRequestedPage() throws Exception {
        insertVacancy("Java Developer", ACME_ID, "Acme", "2026-01-01 10:00:00");
        insertVacancy("Go Developer", ACME_ID, "Acme", "2026-02-01 10:00:00");
        insertVacancy("Rust Developer", ACME_ID, "Acme", "2026-03-01 10:00:00");

        JsonNode page = readJson("/company/" + ACME_ID + "/vacancies?page=1&pageSize=2");

        assertThat(readTitles(page)).containsExactly("Java Developer");
        assertThat(page.get("totalVacancies").asInt()).isEqualTo(3);
        assertThat(page.get("currentPage").asInt()).isEqualTo(1);
    }

    @Test
    void GetCompanyVacancies_UnknownCompany_ShouldReturnEmptyPage() throws Exception {
        JsonNode page = readJson("/company/999999/vacancies?page=0");

        assertThat(page.get("vacancies")).isEmpty();
        assertThat(page.get("totalVacancies").asInt()).isZero();
    }
}
