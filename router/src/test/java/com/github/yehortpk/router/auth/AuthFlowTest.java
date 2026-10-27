package com.github.yehortpk.router.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yehortpk.router.PostgresContainerTest;
import com.github.yehortpk.router.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Drives registration, login, refresh and {@code /me} over the real filter chain and the
 * migrated schema.
 *
 * <p>A {@code @WebMvcTest} slice would mock the repository away and so could not catch a
 * mismatch between the entity and V2, nor confirm that the security chain leaves the
 * parser's and dashboard's public endpoints reachable.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest extends PostgresContainerTest {
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearAccounts() {
        // The container is shared across classes, so each test starts from no accounts
        userRepository.deleteAll();
    }

    private MockHttpServletRequestBuilder jsonPost(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String credentials(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode register(String email) throws Exception {
        return responseBody(mockMvc.perform(jsonPost("/auth/register", credentials(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private String accessTokenOf(String email) throws Exception {
        return register(email).get("accessToken").asText();
    }

    @Test
    void Register_NewEmail_ShouldReturnCreatedWithTokenPair() throws Exception {
        JsonNode tokens = register("new@example.com");

        assertThat(tokens.get("accessToken").asText()).isNotBlank();
        assertThat(tokens.get("refreshToken").asText()).isNotBlank();
    }

    @Test
    void Register_NewEmail_ShouldNotStoreThePasswordInClear() throws Exception {
        register("hashed@example.com");

        String storedHash = userRepository.findByEmail("hashed@example.com").orElseThrow().getPasswordHash();
        assertThat(storedHash).isNotEqualTo(PASSWORD).startsWith("$2");
    }

    @Test
    void Register_EmailAlreadyRegistered_ShouldReturnConflict() throws Exception {
        register("taken@example.com");

        mockMvc.perform(jsonPost("/auth/register", credentials("taken@example.com", PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void Register_EmailDifferingOnlyInCase_ShouldReturnConflict() throws Exception {
        // Email is the login identifier, so Taken@ and taken@ must not be two accounts
        register("case@example.com");

        mockMvc.perform(jsonPost("/auth/register", credentials("CASE@example.com", PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void Register_PasswordShorterThanMinimum_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(jsonPost("/auth/register", credentials("short@example.com", "tiny")))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findByEmail("short@example.com")).isEmpty();
    }

    @Test
    void Register_MalformedEmail_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(jsonPost("/auth/register", credentials("not-an-email", PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void Login_CorrectPassword_ShouldReturnTokenPair() throws Exception {
        register("login@example.com");

        mockMvc.perform(jsonPost("/auth/login", credentials("login@example.com", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void Login_WrongPassword_ShouldReturnUnauthorized() throws Exception {
        register("wrongpass@example.com");

        mockMvc.perform(jsonPost("/auth/login", credentials("wrongpass@example.com", "not-the-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void Login_UnknownEmail_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(jsonPost("/auth/login", credentials("nobody@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void GetAccount_ValidAccessToken_ShouldReturnTheCallersOwnAccount() throws Exception {
        String accessToken = accessTokenOf("account@example.com");

        mockMvc.perform(get("/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("account@example.com"))
                .andExpect(jsonPath("$.hasApiKey").value(false))
                .andExpect(jsonPath("$.apiKeyPrefix").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void GetAccount_NoAuthentication_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void GetAccount_GarbageToken_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/me").header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void GetAccount_RefreshTokenUsedAsBearer_ShouldReturnUnauthorized() throws Exception {
        // A refresh token outlives the access token by weeks; accepting it here would hand
        // that lifetime to every authenticated endpoint
        String refreshToken = register("refreshasbearer@example.com").get("refreshToken").asText();

        mockMvc.perform(get("/me").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void Refresh_ValidRefreshToken_ShouldReturnAWorkingAccessToken() throws Exception {
        String refreshToken = register("refresh@example.com").get("refreshToken").asText();

        MvcResult refreshed = mockMvc.perform(
                        jsonPost("/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        String newAccessToken = responseBody(refreshed).get("accessToken").asText();
        mockMvc.perform(get("/me").header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("refresh@example.com"));
    }

    @Test
    void Refresh_AccessTokenInsteadOfRefreshToken_ShouldReturnUnauthorized() throws Exception {
        String accessToken = accessTokenOf("accessforrefresh@example.com");

        mockMvc.perform(jsonPost("/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(accessToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void Refresh_TokenOfADeletedAccount_ShouldReturnUnauthorized() throws Exception {
        String refreshToken = register("deleted@example.com").get("refreshToken").asText();
        userRepository.deleteAll();

        mockMvc.perform(jsonPost("/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void GetHealth_NoAuthentication_ShouldNotReturnUnauthorized() throws Exception {
        // The compose healthcheck gates the bot's startup on this staying reachable without
        // credentials. The status itself is 503 here, because the Kafka listener indicator
        // reports down with no broker running — what matters is that it is not a 401.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void GetVacancies_NoAuthentication_ShouldNotReturnUnauthorized() throws Exception {
        // The parser and the anonymous dashboard both call the ingest endpoints without
        // credentials; adding security must not have closed them
        mockMvc.perform(get("/vacancy").param("page", "0"))
                .andExpect(status().isOk());
    }

    @Test
    void PostVacancy_NoAuthentication_ShouldNotReturnUnauthorized() throws Exception {
        // The parser writes with no credentials at all; a 401 here would break ingest
        mockMvc.perform(jsonPost("/vacancy", """
                        {"companyTitle":"Probe","title":"Probe engineer","link":"https://example.com/p/1",\
                        "minSalary":0,"maxSalary":0,"remote":false}"""))
                .andExpect(status().is(not(UNAUTHORIZED.value())));
    }
}
