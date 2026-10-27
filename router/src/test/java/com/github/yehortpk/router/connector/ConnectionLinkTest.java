package com.github.yehortpk.router.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yehortpk.router.PostgresContainerTest;
import com.github.yehortpk.router.repositories.TelegramConnectionRepository;
import com.github.yehortpk.router.repositories.UserRepository;
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
 * Drives API key generation, the bot's connector endpoints and the account panel's
 * connection list over the real filter chain and the migrated schema.
 *
 * <p>Several of these guard rules the database cannot enforce on its own — the service-token
 * requirement on {@code /connector/**}, one chat belonging to one account, and a connection
 * id being usable only by its owner — so they are exercised through HTTP rather than against
 * the services directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConnectionLinkTest extends PostgresContainerTest {
    private static final String PASSWORD = "correct-horse-battery";
    private static final long CHAT_ID = 473123822L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TelegramConnectionRepository telegramConnectionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${service.connector-token}")
    private String serviceToken;

    @BeforeEach
    void clearAccounts() {
        // The container is shared across classes; connections go first, as they reference users
        telegramConnectionRepository.deleteAll();
        userRepository.deleteAll();
    }

    private MockHttpServletRequestBuilder jsonPost(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String accessTokenOf(String email) throws Exception {
        MvcResult registered = mockMvc.perform(jsonPost("/auth/register",
                        "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseBody(registered).get("accessToken").asText();
    }

    private String apiKeyOf(String accessToken) throws Exception {
        MvcResult generated = mockMvc.perform(
                        post("/me/api-key").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return responseBody(generated).get("apiKey").asText();
    }

    private MockHttpServletRequestBuilder serviceLink(long chatId, String apiKey) {
        return jsonPost("/connector/telegram/link",
                "{\"chatId\":%d,\"apiKey\":\"%s\"}".formatted(chatId, apiKey))
                .header("X-Service-Token", serviceToken);
    }

    private long linkedConnectionId(String accessToken, long chatId) throws Exception {
        String apiKey = apiKeyOf(accessToken);
        MvcResult linked = mockMvc.perform(serviceLink(chatId, apiKey))
                .andExpect(status().isOk())
                .andReturn();
        return responseBody(linked).get("id").asLong();
    }

    @Test
    void GenerateApiKey_Authenticated_ShouldReturnTheKeyOnceAndRecordItsPrefix() throws Exception {
        String accessToken = accessTokenOf("apikey@example.com");

        String apiKey = apiKeyOf(accessToken);

        assertThat(apiKey).startsWith("vv_");
        // /me reports that a key exists and its prefix, but never the key itself
        mockMvc.perform(get("/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasApiKey").value(true))
                .andExpect(jsonPath("$.apiKeyPrefix").isNotEmpty());
    }

    @Test
    void GenerateApiKey_NoAuthentication_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/me/api-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void GenerateApiKey_Twice_ShouldInvalidatePreviousKey() throws Exception {
        String accessToken = accessTokenOf("regen@example.com");
        String firstKey = apiKeyOf(accessToken);
        String secondKey = apiKeyOf(accessToken);

        mockMvc.perform(serviceLink(CHAT_ID, firstKey))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(serviceLink(CHAT_ID, secondKey))
                .andExpect(status().isOk());
    }

    @Test
    void LinkTelegramConnection_ValidApiKey_ShouldCreateConnection() throws Exception {
        String accessToken = accessTokenOf("link@example.com");
        String apiKey = apiKeyOf(accessToken);

        mockMvc.perform(serviceLink(CHAT_ID, apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("TELEGRAM"))
                .andExpect(jsonPath("$.address").value(String.valueOf(CHAT_ID)))
                .andExpect(jsonPath("$.linkedAt").isNotEmpty());

        assertThat(telegramConnectionRepository.findByChatId(CHAT_ID)).isPresent();
    }

    @Test
    void LinkTelegramConnection_NoServiceToken_ShouldReturnUnauthorized() throws Exception {
        // The connector is reachable only by the bot; without this the API key alone would
        // be enough for anyone who can reach the router
        String apiKey = apiKeyOf(accessTokenOf("notoken@example.com"));

        mockMvc.perform(jsonPost("/connector/telegram/link",
                        "{\"chatId\":%d,\"apiKey\":\"%s\"}".formatted(CHAT_ID, apiKey)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void LinkTelegramConnection_WrongServiceToken_ShouldReturnUnauthorized() throws Exception {
        String apiKey = apiKeyOf(accessTokenOf("badtoken@example.com"));

        mockMvc.perform(jsonPost("/connector/telegram/link",
                        "{\"chatId\":%d,\"apiKey\":\"%s\"}".formatted(CHAT_ID, apiKey))
                        .header("X-Service-Token", "not-the-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void LinkTelegramConnection_InvalidApiKey_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(serviceLink(CHAT_ID, "vv_nosuch_key"))
                .andExpect(status().isUnauthorized());

        assertThat(telegramConnectionRepository.findByChatId(CHAT_ID)).isEmpty();
    }

    @Test
    void LinkTelegramConnection_ChatAlreadyLinkedToAnotherUser_ShouldReturnConflict() throws Exception {
        linkedConnectionId(accessTokenOf("first@example.com"), CHAT_ID);
        String otherKey = apiKeyOf(accessTokenOf("second@example.com"));

        mockMvc.perform(serviceLink(CHAT_ID, otherKey))
                .andExpect(status().isConflict());

        // The chat must still belong to whoever linked it first
        assertThat(telegramConnectionRepository.findByChatId(CHAT_ID).orElseThrow()
                .getUser().getEmail()).isEqualTo("first@example.com");
    }

    @Test
    void LinkTelegramConnection_SameChatAndSameAccountTwice_ShouldReturnTheExistingConnection() throws Exception {
        // Running /link again is a user mistake, not an error
        String accessToken = accessTokenOf("relink@example.com");
        String apiKey = apiKeyOf(accessToken);

        MvcResult first = mockMvc.perform(serviceLink(CHAT_ID, apiKey)).andExpect(status().isOk()).andReturn();
        MvcResult second = mockMvc.perform(serviceLink(CHAT_ID, apiKey)).andExpect(status().isOk()).andReturn();

        assertThat(responseBody(second).get("id").asLong())
                .isEqualTo(responseBody(first).get("id").asLong());
        assertThat(telegramConnectionRepository.findAll()).hasSize(1);
    }

    @Test
    void GetConnections_Authenticated_ShouldReturnOnlyTheCallersConnections() throws Exception {
        String ownerToken = accessTokenOf("owner@example.com");
        linkedConnectionId(ownerToken, CHAT_ID);
        String strangerToken = accessTokenOf("stranger@example.com");
        linkedConnectionId(strangerToken, 999000111L);

        mockMvc.perform(get("/me/connections").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].address").value(String.valueOf(CHAT_ID)));
    }

    @Test
    void GetConnections_NoAuthentication_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/me/connections"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void RenameConnection_OwnConnection_ShouldUpdateTheDisplayTitle() throws Exception {
        String accessToken = accessTokenOf("rename@example.com");
        long connectionId = linkedConnectionId(accessToken, CHAT_ID);

        mockMvc.perform(patch("/me/connections/TELEGRAM/" + connectionId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Work laptop\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Work laptop"));
    }

    @Test
    void RenameConnection_ConnectionOwnedByAnotherUser_ShouldReturnNotFound() throws Exception {
        long strangersConnectionId = linkedConnectionId(accessTokenOf("victim@example.com"), CHAT_ID);
        String attackerToken = accessTokenOf("attacker@example.com");

        mockMvc.perform(patch("/me/connections/TELEGRAM/" + strangersConnectionId)
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"hijacked\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void DeleteConnection_OwnConnection_ShouldUnlinkIt() throws Exception {
        String accessToken = accessTokenOf("unlink@example.com");
        long connectionId = linkedConnectionId(accessToken, CHAT_ID);

        mockMvc.perform(delete("/me/connections/TELEGRAM/" + connectionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        assertThat(telegramConnectionRepository.findByChatId(CHAT_ID)).isEmpty();
    }

    @Test
    void DeleteConnection_ConnectionOwnedByAnotherUser_ShouldReturnNotFound() throws Exception {
        // 404 rather than 403: a 403 would confirm the id exists, making ids enumerable
        long strangersConnectionId = linkedConnectionId(accessTokenOf("keepme@example.com"), CHAT_ID);
        String attackerToken = accessTokenOf("thief@example.com");

        mockMvc.perform(delete("/me/connections/TELEGRAM/" + strangersConnectionId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());

        assertThat(telegramConnectionRepository.findByChatId(CHAT_ID)).isPresent();
    }

    @Test
    void UnlinkTelegramConnection_LinkedChat_ShouldRemoveTheConnection() throws Exception {
        linkedConnectionId(accessTokenOf("botunlink@example.com"), CHAT_ID);

        mockMvc.perform(delete("/connector/telegram")
                        .header("X-Service-Token", serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":%d}".formatted(CHAT_ID)))
                .andExpect(status().isOk());

        assertThat(telegramConnectionRepository.findByChatId(CHAT_ID)).isEmpty();
    }

    @Test
    void UnlinkTelegramConnection_ChatThatWasNeverLinked_ShouldSucceed() throws Exception {
        // /unlink on an unlinked chat is already in the desired state, not an error
        mockMvc.perform(delete("/connector/telegram")
                        .header("X-Service-Token", serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":123456}"))
                .andExpect(status().isOk());
    }

    @Test
    void GetTelegramStatus_LinkedChat_ShouldReportTheOwningAccount() throws Exception {
        linkedConnectionId(accessTokenOf("status@example.com"), CHAT_ID);

        mockMvc.perform(get("/connector/telegram/status")
                        .param("chatId", String.valueOf(CHAT_ID))
                        .header("X-Service-Token", serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.email").value("status@example.com"));
    }

    @Test
    void GetTelegramStatus_UnlinkedChat_ShouldReportNotLinked() throws Exception {
        mockMvc.perform(get("/connector/telegram/status")
                        .param("chatId", "5550000")
                        .header("X-Service-Token", serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(false))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void GetTelegramStatus_NoServiceToken_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/connector/telegram/status").param("chatId", String.valueOf(CHAT_ID)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void GetTelegramStatus_UserAccessTokenInsteadOfServiceToken_ShouldReturnForbidden() throws Exception {
        // A logged-in user is authenticated but is not the service; the connector is not
        // part of the browser-facing API
        String accessToken = accessTokenOf("notaservice@example.com");

        mockMvc.perform(get("/connector/telegram/status")
                        .param("chatId", String.valueOf(CHAT_ID))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void DeleteAccount_UserWithAConnection_ShouldCascadeToItsConnections() throws Exception {
        // telegram_connection.user_id is ON DELETE CASCADE, so removing an account must not
        // leave its destinations behind
        String accessToken = accessTokenOf("cascade@example.com");
        linkedConnectionId(accessToken, CHAT_ID);

        userRepository.deleteAll();

        assertThat(telegramConnectionRepository.findByChatId(CHAT_ID)).isEmpty();
    }
}
