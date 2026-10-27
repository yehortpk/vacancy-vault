package com.github.yehortpk.router.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yehortpk.router.PostgresContainerTest;
import com.github.yehortpk.router.models.parser.ParsingProgressDTO;
import com.github.yehortpk.router.models.response.APIResponse;
import com.github.yehortpk.router.models.user.AppUser;
import com.github.yehortpk.router.models.user.UserRole;
import com.github.yehortpk.router.repositories.UserRepository;
import com.github.yehortpk.router.services.ParsingHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks the three audiences the router serves over the real filter chain: anonymous guests
 * on the dashboard, signed-in users in their own room under {@code /me}, and admins in the
 * parser room.
 *
 * <p>The parser service and the Mongo-backed history are mocked: what is under test is who
 * gets through the chain, not what the parser does once they have.
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

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private ParsingHistoryService parsingHistoryService;

    @BeforeEach
    void clearAccounts() {
        userRepository.deleteAll();
    }

    @BeforeEach
    void stubParserService() {
        when(restTemplate.postForEntity(anyString(), isNull(), eq(APIResponse.class)))
                .thenReturn(ResponseEntity.ok(new APIResponse(200, null)));
        when(restTemplate.getForEntity(anyString(), eq(ParsingProgressDTO.class)))
                .thenReturn(ResponseEntity.ok(new ParsingProgressDTO()));
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

    @Test
    void StartParsing_NoAuthentication_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/parser/start"))
                .andExpect(status().isUnauthorized());

        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(APIResponse.class));
    }

    @Test
    void StartParsing_UserRole_ShouldReturnForbidden() throws Exception {
        String accessToken = registerUser("user-start@example.com");

        mockMvc.perform(post("/parser/start").header("Authorization", bearer(accessToken)))
                .andExpect(status().isForbidden());

        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(APIResponse.class));
    }

    @Test
    void StartParsing_AdminRole_ShouldReachTheParser() throws Exception {
        String accessToken = registerAdmin("admin-start@example.com");

        mockMvc.perform(post("/parser/start").header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk());

        verify(restTemplate).postForEntity(anyString(), isNull(), eq(APIResponse.class));
    }

    @Test
    void GetParsingProgress_NoAuthentication_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/parser/progress"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void GetParsingProgress_UserRole_ShouldReturnForbidden() throws Exception {
        String accessToken = registerUser("user-progress@example.com");

        mockMvc.perform(get("/parser/progress").header("Authorization", bearer(accessToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void GetParsingProgress_AdminRole_ShouldReturnOk() throws Exception {
        String accessToken = registerAdmin("admin-progress@example.com");

        mockMvc.perform(get("/parser/progress").header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk());
    }

    @Test
    void SaveParsingProgress_NoAuthentication_ShouldReturnOk() throws Exception {
        // The parser reports progress here with no credentials; the admin rule on the rest
        // of /parser must not have closed it
        mockMvc.perform(jsonPost("/parser/progress", "{}"))
                .andExpect(status().isOk());

        verify(parsingHistoryService).saveParsingProgress(any());
    }

    @Test
    void GetVacancies_NoAuthentication_ShouldReturnOk() throws Exception {
        // The guest dashboard
        mockMvc.perform(get("/vacancy").param("page", "0"))
                .andExpect(status().isOk());
    }
}
