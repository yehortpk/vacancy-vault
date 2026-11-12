package com.github.yehortpk.router.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yehortpk.router.PostgresContainerTest;
import com.github.yehortpk.router.providers.stub.StubConnectionProvider;
import com.github.yehortpk.router.repositories.FilterRepository;
import com.github.yehortpk.router.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Binding filters to connections. The foreign key on {@code filter_connection} proves that a
 * connection exists, not that it is the caller's, so binding to someone else's chat is refused
 * here or nowhere.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FilterBindingTest extends PostgresContainerTest {
    private static final String PASSWORD = "correct-horse-battery";
    private static final String CONNECTOR = "/connector/" + StubConnectionProvider.PROVIDER_ID;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FilterRepository filterRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${service.connector-token}")
    private String serviceToken;

    @BeforeEach
    void clearBefore() {
        // Connections, link sessions and filters all cascade from their account
        userRepository.deleteAll();
    }

    @AfterEach
    void clearAfter() {
        userRepository.deleteAll();
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

    /** Links a stub destination through the real link flow and returns its connection id. */
    private long linkedConnectionId(String accessToken, String address) throws Exception {
        MvcResult started = mockMvc.perform(post("/me/connections/" + StubConnectionProvider.PROVIDER_ID + "/link")
                        .header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andReturn();
        String token = responseBody(started).get("token").asText();

        MvcResult linked = mockMvc.perform(withBody(post(CONNECTOR + "/linked"),
                                "{\"token\":\"%s\",\"destination\":{\"address\":\"%s\"}}".formatted(token, address))
                        .header("X-Service-Token", serviceToken))
                .andExpect(status().isOk())
                .andReturn();
        return responseBody(linked).get("id").asLong();
    }

    private static String filterBody(long... connectionIds) {
        StringBuilder ids = new StringBuilder();
        for (long connectionId : connectionIds) {
            ids.append(ids.isEmpty() ? "" : ",").append(connectionId);
        }
        return "{\"title\":\"Java\",\"filter\":\"java\",\"connectionIds\":[" + ids + "]}";
    }

    private long createdFilterId(String accessToken, String body) throws Exception {
        MvcResult created = mockMvc.perform(withBody(post("/me/filters"), body)
                        .header("Authorization", accessToken))
                .andExpect(status().isCreated())
                .andReturn();
        return responseBody(created).get("filterId").asLong();
    }

    @Test
    void CreateFilter_OwnConnection_ShouldBindIt() throws Exception {
        String accessToken = accessTokenOf("bind@example.com");
        long connectionId = linkedConnectionId(accessToken, "stub-bind");

        mockMvc.perform(withBody(post("/me/filters"), filterBody(connectionId))
                        .header("Authorization", accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.connectionIds[0]").value(connectionId));
    }

    @Test
    void CreateFilter_SameConnectionTwice_ShouldBindItOnce() throws Exception {
        String accessToken = accessTokenOf("twice@example.com");
        long connectionId = linkedConnectionId(accessToken, "stub-twice");

        mockMvc.perform(withBody(post("/me/filters"), filterBody(connectionId, connectionId))
                        .header("Authorization", accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.connectionIds.length()").value(1));
    }

    @Test
    void BindFilter_ConnectionOwnedByAnotherUser_ShouldReturnNotFound() throws Exception {
        // The key check: without it a filter could be bound to a stranger's chat and spam it
        long strangersConnectionId = linkedConnectionId(accessTokenOf("victim@example.com"), "stub-victim");
        String attackerToken = accessTokenOf("spammer@example.com");

        mockMvc.perform(withBody(post("/me/filters"), filterBody(strangersConnectionId))
                        .header("Authorization", attackerToken))
                .andExpect(status().isNotFound());

        assertThat(filterRepository.findAll()).isEmpty();
    }

    @Test
    void UpdateFilter_ConnectionOwnedByAnotherUser_ShouldReturnNotFoundAndKeepTheBindings() throws Exception {
        long strangersConnectionId = linkedConnectionId(accessTokenOf("other@example.com"), "stub-other");
        String accessToken = accessTokenOf("rebind@example.com");
        long ownConnectionId = linkedConnectionId(accessToken, "stub-own");
        long filterId = createdFilterId(accessToken, filterBody(ownConnectionId));

        mockMvc.perform(withBody(put("/me/filters/" + filterId), filterBody(ownConnectionId, strangersConnectionId))
                        .header("Authorization", accessToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/me/filters/" + filterId).header("Authorization", accessToken))
                .andExpect(jsonPath("$.connectionIds.length()").value(1))
                .andExpect(jsonPath("$.connectionIds[0]").value(ownConnectionId));
    }

    @Test
    void UpdateFilter_EmptyConnectionIds_ShouldMakeItADraft() throws Exception {
        String accessToken = accessTokenOf("unbind@example.com");
        long connectionId = linkedConnectionId(accessToken, "stub-unbind");
        long filterId = createdFilterId(accessToken, filterBody(connectionId));

        mockMvc.perform(withBody(put("/me/filters/" + filterId), filterBody())
                        .header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionIds").isEmpty());
    }

    @Test
    void DeleteConnection_ConnectionWithBoundFilters_ShouldLeaveFiltersAsDrafts() throws Exception {
        String accessToken = accessTokenOf("drafts@example.com");
        long connectionId = linkedConnectionId(accessToken, "stub-drafts");
        long filterId = createdFilterId(accessToken, filterBody(connectionId));

        mockMvc.perform(delete("/me/connections/" + connectionId).header("Authorization", accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/me/filters/" + filterId).header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Java"))
                .andExpect(jsonPath("$.connectionIds").isEmpty());
    }

    @Test
    void GetStatus_DestinationWithFilters_ShouldCountAllFiltersAndTheBoundOnes() throws Exception {
        // The bot's /status: every filter of the account, and those delivering to this chat
        String accessToken = accessTokenOf("counts@example.com");
        long connectionId = linkedConnectionId(accessToken, "stub-counts");
        long otherConnectionId = linkedConnectionId(accessToken, "stub-counts-other");
        createdFilterId(accessToken, filterBody(connectionId));
        createdFilterId(accessToken, filterBody(connectionId, otherConnectionId));
        createdFilterId(accessToken, filterBody(otherConnectionId));
        createdFilterId(accessToken, filterBody());

        mockMvc.perform(get(CONNECTOR + "/status")
                        .param("address", "stub-counts")
                        .header("X-Service-Token", serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.filterCount").value(4))
                .andExpect(jsonPath("$.activeFilterCount").value(2));
    }
}
