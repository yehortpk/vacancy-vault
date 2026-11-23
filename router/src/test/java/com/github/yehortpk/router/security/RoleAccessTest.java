package com.github.yehortpk.router.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yehortpk.router.PostgresContainerTest;
import com.github.yehortpk.router.models.user.AppUser;
import com.github.yehortpk.router.models.user.UserRole;
import com.github.yehortpk.router.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks the roles of the router's accounts over the real filter chain: every account gets
 * its own room under {@code /me}, and a registration cannot claim more.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleAccessTest extends PostgresContainerTest {
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearAccounts() {
        userRepository.deleteAll();
    }

    private MockHttpServletRequestBuilder jsonPost(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String credentials(String email) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD);
    }

    private String accessTokenOf(String email) throws Exception {
        String body = mockMvc.perform(jsonPost("/auth/login", credentials(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String registerUser(String email) throws Exception {
        mockMvc.perform(jsonPost("/auth/register", credentials(email)))
                .andExpect(status().isCreated());
        return accessTokenOf(email);
    }

    /**
     * Registers, promotes in the database the way an operator would, then logs in again,
     * since the role is read into the token at issue time.
     */
    private String registerAdmin(String email) throws Exception {
        registerUser(email);
        AppUser user = userRepository.findByEmail(email).orElseThrow();
        user.setRole(UserRole.ADMIN);
        userRepository.save(user);
        return accessTokenOf(email);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    @Test
    void Register_BodyClaimingAdminRole_ShouldCreateAPlainUser() throws Exception {
        // The request DTO has no role field; an extra one in the JSON must not escalate
        mockMvc.perform(jsonPost("/auth/register",
                        "{\"email\":\"sneaky@example.com\",\"password\":\"%s\",\"role\":\"ADMIN\"}"
                                .formatted(PASSWORD)))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmail("sneaky@example.com").orElseThrow().getRole())
                .isEqualTo(UserRole.USER);
    }

    @Test
    void GetAccount_NewAccount_ShouldReportTheUserRole() throws Exception {
        String accessToken = registerUser("plain@example.com");

        mockMvc.perform(get("/me").header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void GetAccount_AdminRole_ShouldReachItsOwnRoom() throws Exception {
        String accessToken = registerAdmin("admin-me@example.com");

        mockMvc.perform(get("/me").header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}
