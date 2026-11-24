package com.github.yehortpk.router.security;

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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who gets into an account's own room under {@code /me}, over the real filter chain: nobody
 * anonymous, any signed-in account. There are no roles to tell accounts apart.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountAccessTest extends PostgresContainerTest {
    private static final String PASSWORD = "correct-horse-battery";
    private static final String[] ACCOUNT_PATHS = {"/me", "/me/providers", "/me/connections", "/me/filters"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearBefore() {
        userRepository.deleteAll();
    }

    @AfterEach
    void clearAfter() {
        userRepository.deleteAll();
    }

    private String registerUser(String email, String extraFields) throws Exception {
        String body = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"%s}".formatted(email, PASSWORD, extraFields)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("accessToken").asText();
    }

    @Test
    void GetAccountPaths_NoAuthentication_ShouldReturnUnauthorized() throws Exception {
        for (String path : ACCOUNT_PATHS) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void GetAccountPaths_LoggedInUser_ShouldReturnOk() throws Exception {
        String accessToken = registerUser("plain@example.com", "");

        for (String path : ACCOUNT_PATHS) {
            mockMvc.perform(get(path).header("Authorization", accessToken)).andExpect(status().isOk());
        }
    }

    @Test
    void GetAccount_LoggedInUser_ShouldReportNoRole() throws Exception {
        String accessToken = registerUser("norole@example.com", "");

        mockMvc.perform(get("/me").header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("norole@example.com"))
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    void Register_BodyClaimingAdminRole_ShouldCreateAnOrdinaryAccount() throws Exception {
        // The request DTO has no role field; an extra one in the JSON changes nothing
        String accessToken = registerUser("sneaky@example.com", ",\"role\":\"ADMIN\"");

        mockMvc.perform(get("/me").header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    void GetAccount_ServiceToken_ShouldReturnForbidden() throws Exception {
        // The provider services' shared secret opens /connector only, never somebody's account
        mockMvc.perform(get("/me").header("X-Service-Token", "test-service-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void GetParserProgress_LoggedInUser_ShouldReturnNotFound() throws Exception {
        // The parser room moved to the parser's own panel; the router no longer proxies it
        String accessToken = registerUser("curious@example.com", "");

        mockMvc.perform(get("/parser/progress").header("Authorization", accessToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/vacancy").param("page", "0"))
                .andExpect(status().isNotFound());
    }
}
