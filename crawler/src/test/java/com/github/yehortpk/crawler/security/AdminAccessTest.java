package com.github.yehortpk.crawler.security;

import com.github.yehortpk.crawler.PostgresContainerTest;
import com.github.yehortpk.crawler.models.admin.Admin;
import com.github.yehortpk.crawler.repositories.AdminRepository;
import com.github.yehortpk.crawler.services.CrawlerRunnerService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who gets into the crawler room, over the real filter chain and the migrated {@code admin}
 * table: nobody without a session, an admin after logging in. The CSRF token is used the way
 * the panel uses it, read from the cookie and sent back in the header.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAccessTest extends PostgresContainerTest {
    private static final String USERNAME = "operator";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Replaced so that {@code /crawler/start} starts no real run. */
    @MockitoBean
    private CrawlerRunnerService crawlerRunnerService;

    @BeforeEach
    void setUp() {
        adminRepository.deleteAll();
        Admin admin = new Admin();
        admin.setUsername(USERNAME);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setCreatedAt(LocalDateTime.now());
        adminRepository.save(admin);
        // The boot run has already called it once
        clearInvocations(crawlerRunnerService);
    }

    @AfterEach
    void tearDown() {
        adminRepository.deleteAll();
    }

    /** The token a browser would have after its first request: every response sets the cookie. */
    private Cookie findCsrfCookie() throws Exception {
        Cookie cookie = mockMvc.perform(get("/crawler/session")).andReturn().getResponse().getCookie(CSRF_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, Cookie csrfCookie) {
        return request.cookie(csrfCookie).header(CSRF_HEADER, csrfCookie.getValue());
    }

    private MockHttpServletRequestBuilder createLoginRequest(String username, String password) {
        return post("/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password));
    }

    private MockHttpSession logIn(Cookie csrfCookie) throws Exception {
        MvcResult result = mockMvc.perform(withCsrf(createLoginRequest(USERNAME, PASSWORD), csrfCookie))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void GetProgress_NoSession_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/crawler/progress"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void GetProgress_NoSession_ShouldNotChallengeWithBasicAuthentication() throws Exception {
        // A challenge would make the browser pop up its own credentials dialog
        MvcResult result = mockMvc.perform(get("/crawler/progress")).andReturn();

        assertThat(result.getResponse().getHeader("WWW-Authenticate")).isNull();
    }

    @Test
    void StartCrawling_NoSession_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(withCsrf(post("/crawler/start"), findCsrfCookie()))
                .andExpect(status().isUnauthorized());

        verify(crawlerRunnerService, never()).runCrawling();
    }

    @Test
    void Login_CorrectPassword_ShouldOpenTheCrawlerRoom() throws Exception {
        Cookie csrfCookie = findCsrfCookie();
        MockHttpSession session = logIn(csrfCookie);

        mockMvc.perform(get("/crawler/progress").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDLE"));
        mockMvc.perform(get("/crawler/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME));
    }

    @Test
    void Login_WrongPassword_ShouldReturnUnauthorized() throws Exception {
        Cookie csrfCookie = findCsrfCookie();

        MvcResult result = mockMvc.perform(withCsrf(createLoginRequest(USERNAME, "wrong-password"), csrfCookie))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void Login_UnknownUsername_ShouldAnswerLikeAWrongPassword() throws Exception {
        Cookie csrfCookie = findCsrfCookie();

        mockMvc.perform(withCsrf(createLoginRequest("nobody", PASSWORD), csrfCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void Login_NoCsrfToken_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(createLoginRequest(USERNAME, PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void StartCrawling_LoggedInAdmin_ShouldStartARun() throws Exception {
        Cookie csrfCookie = findCsrfCookie();
        MockHttpSession session = logIn(csrfCookie);

        mockMvc.perform(withCsrf(post("/crawler/start"), csrfCookie).session(session))
                .andExpect(status().isOk());

        verify(crawlerRunnerService).runCrawling();
    }

    @Test
    void StartCrawling_LoggedInAdminWithoutCsrfToken_ShouldReturnForbidden() throws Exception {
        // The session cookie alone is what a forged cross-site request would carry
        MockHttpSession session = logIn(findCsrfCookie());

        mockMvc.perform(post("/crawler/start").session(session))
                .andExpect(status().isForbidden());

        verify(crawlerRunnerService, never()).runCrawling();
    }

    @Test
    void GetProgress_BearerTokenOfARouterUser_ShouldReturnUnauthorized() throws Exception {
        // The router's users mean nothing here, whatever their token says
        mockMvc.perform(get("/crawler/progress").header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.e30.x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void Logout_LoggedInAdmin_ShouldCloseTheCrawlerRoom() throws Exception {
        Cookie csrfCookie = findCsrfCookie();
        MockHttpSession session = logIn(csrfCookie);

        mockMvc.perform(withCsrf(post("/logout"), csrfCookie).session(session))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void GetHealth_NoSession_ShouldReturnOk() throws Exception {
        // Mongo and Kafka are not running here, so only reachability is asserted, not UP
        MvcResult result = mockMvc.perform(get("/actuator/health")).andReturn();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }
}
