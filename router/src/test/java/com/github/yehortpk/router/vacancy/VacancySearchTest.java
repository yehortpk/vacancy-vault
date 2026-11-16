package com.github.yehortpk.router.vacancy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yehortpk.router.PostgresContainerTest;
import com.github.yehortpk.router.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The {@code remote} and {@code withSalary} conditions on both vacancy listings: the public one,
 * narrowed in the database, and a saved filter's, narrowed in memory. Both must agree.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VacancySearchTest extends PostgresContainerTest {
    private static final int COMPANY_ID = 1;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private void clearData() {
        // Other classes count rows in the shared container
        userRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM vacancy");
        jdbcTemplate.update("DELETE FROM company");
    }

    @BeforeEach
    void setUp() {
        clearData();
        jdbcTemplate.update("INSERT INTO company (company_id, title, parsing_enabled) VALUES (?, 'Acme', false)",
                COMPANY_ID);
        insertVacancy("Java Office No Salary", false, null, null);
        insertVacancy("Java Remote No Salary", true, null, null);
        insertVacancy("Java Office Min Salary", false, 1000, null);
        insertVacancy("Java Remote Max Salary", true, null, 3000);
        insertVacancy("Python Remote Range", true, 2000, 4000);
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    private void insertVacancy(String title, boolean isRemote, Integer minSalary, Integer maxSalary) {
        jdbcTemplate.update("INSERT INTO vacancy (is_remote, link, max_salary, min_salary, parsed_at, title, company_id)"
                        + " VALUES (?, ?, ?, ?, now(), ?, ?)",
                isRemote, "https://acme.example/" + title.replace(' ', '-'), maxSalary, minSalary, title, COMPANY_ID);
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

    private void markParsedHoursAgo(String title, int hours) {
        jdbcTemplate.update("UPDATE vacancy SET parsed_at = now() - make_interval(hours => ?) WHERE title = ?",
                hours, title);
    }

    private String registerAccessToken() throws Exception {
        MvcResult registered = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"search@example.com\",\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return "Bearer " + objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private long createFilterId(String accessToken, String expression) throws Exception {
        MvcResult created = mockMvc.perform(post("/me/filters").header("Authorization", accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Java\",\"filter\":\"%s\"}".formatted(expression)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("filterId").asLong();
    }

    @Test
    void GetVacancies_DifferentParseTimes_ShouldListNewestFirst() throws Exception {
        markParsedHoursAgo("Java Office No Salary", 1);
        markParsedHoursAgo("Java Remote No Salary", 3);
        markParsedHoursAgo("Java Office Min Salary", 2);

        // "Java Remote Max Salary" keeps the parse time of now()
        assertThat(titlesOf(get("/vacancy").param("query", "Java"))).containsExactly(
                "Java Remote Max Salary", "Java Office No Salary", "Java Office Min Salary", "Java Remote No Salary");
    }

    @Test
    void GetVacanciesByFilter_DifferentParseTimes_ShouldListNewestFirst() throws Exception {
        markParsedHoursAgo("Java Office No Salary", 5);
        markParsedHoursAgo("Java Remote No Salary", 4);
        markParsedHoursAgo("Java Office Min Salary", 3);
        markParsedHoursAgo("Java Remote Max Salary", 2);
        String accessToken = registerAccessToken();
        String filterVacanciesPath = "/me/filters/" + createFilterId(accessToken, "java") + "/vacancies";

        assertThat(titlesOf(get(filterVacanciesPath).header("Authorization", accessToken))).containsExactly(
                "Java Remote Max Salary", "Java Office Min Salary", "Java Remote No Salary", "Java Office No Salary");
    }

    @Test
    void GetVacancies_NoConditions_ShouldReturnEveryVacancy() throws Exception {
        assertThat(titlesOf(get("/vacancy"))).hasSize(5);
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
    void GetVacanciesByFilter_RemoteAndSalaryRequired_ShouldNarrowTheFilterMatches() throws Exception {
        String accessToken = registerAccessToken();
        String filterVacanciesPath = "/me/filters/" + createFilterId(accessToken, "java") + "/vacancies";

        assertThat(titlesOf(get(filterVacanciesPath).header("Authorization", accessToken))).hasSize(4);
        assertThat(titlesOf(get(filterVacanciesPath).header("Authorization", accessToken)
                .param("remote", "true").param("withSalary", "true")))
                .containsExactlyInAnyOrder("Java Remote Max Salary");
    }
}
