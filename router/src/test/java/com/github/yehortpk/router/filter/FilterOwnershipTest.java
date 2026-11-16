package com.github.yehortpk.router.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yehortpk.router.PostgresContainerTest;
import com.github.yehortpk.router.repositories.FilterRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The {@code /me/filters} CRUD set over the real filter chain: every filter id is the caller's
 * own or answers 404, and a filter expression is checked when it is written.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FilterOwnershipTest extends PostgresContainerTest {
    private static final String PASSWORD = "correct-horse-battery";
    private static final String FILTER_BODY = "{\"title\":\"Java\",\"filter\":\"java -senior\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FilterRepository filterRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private void clearData() {
        // Filters cascade from their account. The vacancies are this class's own, and other
        // classes count rows in the shared container
        userRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM vacancy");
        jdbcTemplate.update("DELETE FROM company");
    }

    @BeforeEach
    void clearBefore() {
        clearData();
    }

    @AfterEach
    void clearAfter() {
        clearData();
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String accessTokenOf(String email) throws Exception {
        MvcResult registered = mockMvc.perform(withBody(post("/auth/register"),
                        "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return "Bearer " + responseBody(registered).get("accessToken").asText();
    }

    private long createdFilterId(String accessToken, String body) throws Exception {
        MvcResult created = mockMvc.perform(withBody(post("/me/filters"), body)
                        .header("Authorization", accessToken))
                .andExpect(status().isCreated())
                .andReturn();
        return responseBody(created).get("filterId").asLong();
    }

    private void insertVacancy(int companyId, String companyTitle, String title, String link) {
        jdbcTemplate.update("INSERT INTO company (company_id, title, parsing_enabled) VALUES (?, ?, false)"
                + " ON CONFLICT DO NOTHING", companyId, companyTitle);
        jdbcTemplate.update("INSERT INTO vacancy (is_remote, link, max_salary, min_salary, parsed_at, title, company_id)"
                + " VALUES (false, ?, NULL, NULL, now(), ?, ?)", link, title, companyId);
    }

    @Test
    void CreateFilter_NoAuthentication_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(withBody(post("/me/filters"), FILTER_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void CreateFilter_WithoutConnections_ShouldCreateADraft() throws Exception {
        mockMvc.perform(withBody(post("/me/filters"), FILTER_BODY)
                        .header("Authorization", accessTokenOf("draft@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filterId").isNumber())
                .andExpect(jsonPath("$.title").value("Java"))
                .andExpect(jsonPath("$.filter").value("java -senior"))
                .andExpect(jsonPath("$.connectionIds").isEmpty());
    }

    @Test
    void CreateFilter_MalformedExpression_ShouldReturnBadRequest() throws Exception {
        // Stored anyway, it would throw inside the vacancy consumer for every new vacancy
        mockMvc.perform(withBody(post("/me/filters"), "{\"title\":\"Typo\",\"filter\":\"java - senior\"}")
                        .header("Authorization", accessTokenOf("malformed@example.com")))
                .andExpect(status().isBadRequest());

        assertThat(filterRepository.findAll()).isEmpty();
    }

    @Test
    void CreateFilter_BlankTitle_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(withBody(post("/me/filters"), "{\"title\":\"  \",\"filter\":\"java\"}")
                        .header("Authorization", accessTokenOf("untitled@example.com")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void CreateFilter_BlankExpression_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(withBody(post("/me/filters"), "{\"title\":\"Empty\",\"filter\":\"\"}")
                        .header("Authorization", accessTokenOf("empty@example.com")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void GetFilters_Authenticated_ShouldReturnOnlyTheCallersFilters() throws Exception {
        String ownerToken = accessTokenOf("owner@example.com");
        createdFilterId(ownerToken, FILTER_BODY);
        createdFilterId(accessTokenOf("stranger@example.com"), "{\"title\":\"Theirs\",\"filter\":\"python\"}");

        mockMvc.perform(get("/me/filters").header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Java"));
    }

    @Test
    void GetFilters_NoAuthentication_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/me/filters"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void GetFilter_OwnFilter_ShouldReturnIt() throws Exception {
        String accessToken = accessTokenOf("mine@example.com");
        long filterId = createdFilterId(accessToken, FILTER_BODY);

        mockMvc.perform(get("/me/filters/" + filterId).header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filterId").value(filterId));
    }

    @Test
    void GetFilter_FilterOwnedByAnotherUser_ShouldReturnNotFound() throws Exception {
        // 404 rather than 403: a 403 would confirm the id exists, making ids enumerable
        long strangersFilterId = createdFilterId(accessTokenOf("victim@example.com"), FILTER_BODY);

        mockMvc.perform(get("/me/filters/" + strangersFilterId)
                        .header("Authorization", accessTokenOf("attacker@example.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void UpdateFilter_OwnFilter_ShouldReplaceTitleAndExpression() throws Exception {
        String accessToken = accessTokenOf("editor@example.com");
        long filterId = createdFilterId(accessToken, FILTER_BODY);

        mockMvc.perform(withBody(put("/me/filters/" + filterId), "{\"title\":\" Kotlin \",\"filter\":\"kotlin -lead\"}")
                        .header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Kotlin"))
                .andExpect(jsonPath("$.filter").value("kotlin -lead"));
    }

    @Test
    void UpdateFilter_MalformedExpression_ShouldReturnBadRequestAndKeepTheFilter() throws Exception {
        String accessToken = accessTokenOf("typo@example.com");
        long filterId = createdFilterId(accessToken, FILTER_BODY);

        mockMvc.perform(withBody(put("/me/filters/" + filterId), "{\"title\":\"Java\",\"filter\":\"--\"}")
                        .header("Authorization", accessToken))
                .andExpect(status().isBadRequest());

        assertThat(filterRepository.findById(filterId).orElseThrow().getFilter()).isEqualTo("java -senior");
    }

    @Test
    void UpdateFilter_FilterOwnedByAnotherUser_ShouldReturnNotFound() throws Exception {
        long strangersFilterId = createdFilterId(accessTokenOf("keep@example.com"), FILTER_BODY);

        mockMvc.perform(withBody(put("/me/filters/" + strangersFilterId), "{\"title\":\"Mine now\",\"filter\":\"java\"}")
                        .header("Authorization", accessTokenOf("hijacker@example.com")))
                .andExpect(status().isNotFound());

        assertThat(filterRepository.findById(strangersFilterId).orElseThrow().getTitle()).isEqualTo("Java");
    }

    @Test
    void DeleteFilter_OwnFilter_ShouldRemoveIt() throws Exception {
        String accessToken = accessTokenOf("delete@example.com");
        long filterId = createdFilterId(accessToken, FILTER_BODY);

        mockMvc.perform(delete("/me/filters/" + filterId).header("Authorization", accessToken))
                .andExpect(status().isOk());

        assertThat(filterRepository.findById(filterId)).isEmpty();
    }

    @Test
    void DeleteFilter_FilterOwnedByAnotherUser_ShouldReturnNotFound() throws Exception {
        long strangersFilterId = createdFilterId(accessTokenOf("survivor@example.com"), FILTER_BODY);

        mockMvc.perform(delete("/me/filters/" + strangersFilterId)
                        .header("Authorization", accessTokenOf("deleter@example.com")))
                .andExpect(status().isNotFound());

        assertThat(filterRepository.findById(strangersFilterId)).isPresent();
    }

    @Test
    void GetVacanciesByFilter_OwnFilter_ShouldReturnOnlyMatchingVacancies() throws Exception {
        insertVacancy(1, "Acme", "Java Developer", "https://acme.example/1");
        insertVacancy(1, "Acme", "Senior Java Developer", "https://acme.example/2");
        insertVacancy(2, "Globex", "Python Developer", "https://globex.example/1");
        String accessToken = accessTokenOf("vacancies@example.com");
        long filterId = createdFilterId(accessToken, FILTER_BODY);

        mockMvc.perform(get("/me/filters/" + filterId + "/vacancies").param("page", "0")
                        .header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVacancies").value(1))
                .andExpect(jsonPath("$.vacancies[0].title").value("Java Developer"))
                // Zoned, so the browser shows it in the viewer's own time zone
                .andExpect(jsonPath("$.vacancies[0].parsedAt").value(org.hamcrest.Matchers.matchesRegex(
                        ".*([+-]\\d{2}:\\d{2}|Z)$")));
    }

    @Test
    void GetVacanciesByFilter_CompanyKeyword_ShouldMatchTheCompanyTitle() throws Exception {
        insertVacancy(1, "Acme", "Java Developer", "https://acme.example/1");
        insertVacancy(2, "Globex", "Java Developer", "https://globex.example/1");
        String accessToken = accessTokenOf("company@example.com");
        long filterId = createdFilterId(accessToken, "{\"title\":\"Acme only\",\"filter\":\"Acme, java\"}");

        mockMvc.perform(get("/me/filters/" + filterId + "/vacancies").param("page", "0")
                        .header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVacancies").value(1))
                .andExpect(jsonPath("$.vacancies[0].company.title").value("Acme"));
    }

    @Test
    void GetVacanciesByFilter_FilterOwnedByAnotherUser_ShouldReturnNotFound() throws Exception {
        long strangersFilterId = createdFilterId(accessTokenOf("private@example.com"), FILTER_BODY);

        mockMvc.perform(get("/me/filters/" + strangersFilterId + "/vacancies").param("page", "0")
                        .header("Authorization", accessTokenOf("snoop@example.com")))
                .andExpect(status().isNotFound());
    }
}
