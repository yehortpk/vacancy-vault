package com.github.yehortpk.router.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yehortpk.router.PostgresContainerTest;
import com.github.yehortpk.router.models.connection.Connection;
import com.github.yehortpk.router.models.connection.LinkSession;
import com.github.yehortpk.router.models.provider.Provider;
import com.github.yehortpk.router.providers.stub.StubConnectionProvider;
import com.github.yehortpk.router.providers.stub.StubConnectionRepository;
import com.github.yehortpk.router.repositories.ConnectionRepository;
import com.github.yehortpk.router.repositories.LinkSessionRepository;
import com.github.yehortpk.router.repositories.ProviderRepository;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the provider-independent link flow (start a link, the provider's "linked" signal,
 * the session the panel polls) and connection management, with the test-only stub provider,
 * over the real filter chain and the migrated schema.
 *
 * <p>Several of these guard rules the database cannot enforce on its own: the service-token
 * requirement on {@code /connector/**}, a link token being single-use and short-lived, one
 * destination belonging to one account, and a connection id being usable only by its owner.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConnectionLinkTest extends PostgresContainerTest {
    private static final String PASSWORD = "correct-horse-battery";
    private static final String ADDRESS = "stub-473123822";
    private static final long STUB_ID = StubConnectionProvider.PROVIDER_ID;
    private static final String CONNECTOR = "/connector/" + STUB_ID;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private StubConnectionRepository stubConnectionRepository;

    @Autowired
    private LinkSessionRepository linkSessionRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${service.connector-token}")
    private String serviceToken;

    private void setStubEnabled(boolean enabled) {
        Provider stub = providerRepository.findById(STUB_ID).orElseThrow();
        stub.setEnabled(enabled);
        providerRepository.save(stub);
    }

    @BeforeEach
    void clearAccounts() {
        // The container is shared across classes. Connections and link sessions cascade from
        // their account, so deleting the accounts clears everything this class creates
        userRepository.deleteAll();
        setStubEnabled(true);
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

    private JsonNode startStubLink(String accessToken) throws Exception {
        MvcResult started = mockMvc.perform(post("/me/connections/" + STUB_ID + "/link")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return responseBody(started);
    }

    /** The one-time token, returned only by the response that started the session. */
    private String linkTokenOf(JsonNode session) {
        return session.get("token").asText();
    }

    private String issueLinkToken(String accessToken) throws Exception {
        return linkTokenOf(startStubLink(accessToken));
    }

    private MockHttpServletRequestBuilder signalLinkedWithDestination(String token, String destination) {
        return jsonPost(CONNECTOR + "/linked",
                "{\"token\":\"%s\",\"destination\":%s}".formatted(token, destination))
                .header("X-Service-Token", serviceToken);
    }

    private MockHttpServletRequestBuilder signalLinked(String token, String address) {
        return signalLinkedWithDestination(token, "{\"address\":\"%s\"}".formatted(address));
    }

    private long linkedConnectionId(String accessToken, String address) throws Exception {
        MvcResult linked = mockMvc.perform(signalLinked(issueLinkToken(accessToken), address))
                .andExpect(status().isOk())
                .andReturn();
        return responseBody(linked).get("id").asLong();
    }

    private Connection connectionOfAddress(String address) {
        long connectionId = stubConnectionRepository.findConnectionIdByAddress(address).orElseThrow();
        return connectionRepository.findById(connectionId).orElseThrow();
    }

    @Test
    void GetProviders_Authenticated_ShouldListTheStubProvider() throws Exception {
        mockMvc.perform(get("/me/providers").header("Authorization", "Bearer " + accessTokenOf("providers@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(STUB_ID))
                .andExpect(jsonPath("$[0].title").value("Stub"));
    }

    @Test
    void StartLink_Authenticated_ShouldReturnPendingSessionWithRenderedInstructions() throws Exception {
        JsonNode session = startStubLink(accessTokenOf("start@example.com"));
        String token = linkTokenOf(session);

        assertThat(session.get("status").asText()).isEqualTo("PENDING");
        assertThat(session.get("providerId").asLong()).isEqualTo(STUB_ID);
        assertThat(session.get("expiresAt").asText()).isNotEmpty();
        // 32 random bytes, Base64url without padding
        assertThat(token).hasSize(43);
        // The provider row's link_instructions, with the token in place of {token}
        assertThat(session.get("instructions").asText()).isEqualTo(
                "Send /link " + token + " to the stub messenger.");
        // Only a hash of the token is stored
        assertThat(linkSessionRepository.findAll()).extracting(LinkSession::getTokenHash)
                .doesNotContain(linkTokenOf(session));
    }

    @Test
    void StartLink_NoAuthentication_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/me/connections/" + STUB_ID + "/link"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void StartLink_UnknownProvider_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(post("/me/connections/999/link")
                        .header("Authorization", "Bearer " + accessTokenOf("pigeon@example.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void StartLink_DisabledProvider_ShouldReturnNotFound() throws Exception {
        String accessToken = accessTokenOf("disabled@example.com");
        setStubEnabled(false);

        mockMvc.perform(post("/me/connections/" + STUB_ID + "/link").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void StartLink_Twice_ShouldInvalidateThePreviousToken() throws Exception {
        String accessToken = accessTokenOf("restart@example.com");
        String firstToken = issueLinkToken(accessToken);
        String secondToken = issueLinkToken(accessToken);

        mockMvc.perform(signalLinked(firstToken, ADDRESS))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(signalLinked(secondToken, ADDRESS))
                .andExpect(status().isOk());
    }

    @Test
    void CompleteLink_ValidToken_ShouldCreateConnection() throws Exception {
        String token = issueLinkToken(accessTokenOf("link@example.com"));

        mockMvc.perform(signalLinked(token, ADDRESS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value(STUB_ID))
                .andExpect(jsonPath("$.address").value(ADDRESS))
                .andExpect(jsonPath("$.linkedAt").isNotEmpty());

        assertThat(connectionOfAddress(ADDRESS).getUser().getEmail()).isEqualTo("link@example.com");
    }

    @Test
    void CompleteLink_ValidToken_ShouldMarkTheSessionLinked() throws Exception {
        // This is the signal the account panel polls for after starting a link
        String accessToken = accessTokenOf("polled@example.com");
        JsonNode session = startStubLink(accessToken);
        mockMvc.perform(signalLinked(linkTokenOf(session), ADDRESS)).andExpect(status().isOk());

        mockMvc.perform(get("/me/link-sessions/" + session.get("id").asLong())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LINKED"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.instructions").doesNotExist())
                .andExpect(jsonPath("$.connection.address").value(ADDRESS));
    }

    @Test
    void CompleteLink_TokenUsedTwice_ShouldReturnUnauthorized() throws Exception {
        // Whoever presents a token links a destination to its owner, so it must not work a second time
        String token = issueLinkToken(accessTokenOf("once@example.com"));
        mockMvc.perform(signalLinked(token, ADDRESS)).andExpect(status().isOk());

        mockMvc.perform(signalLinked(token, "stub-999000111"))
                .andExpect(status().isUnauthorized());

        assertThat(stubConnectionRepository.findConnectionIdByAddress("stub-999000111")).isEmpty();
    }

    @Test
    void CompleteLink_ExpiredToken_ShouldReturnUnauthorized() throws Exception {
        String accessToken = accessTokenOf("expired@example.com");
        JsonNode session = startStubLink(accessToken);
        LinkSession stored = linkSessionRepository.findById(session.get("id").asLong()).orElseThrow();
        stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        linkSessionRepository.save(stored);

        mockMvc.perform(signalLinked(linkTokenOf(session), ADDRESS))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/me/link-sessions/" + stored.getId()).header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void CompleteLink_UnknownToken_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(signalLinked("not-a-token", ADDRESS))
                .andExpect(status().isUnauthorized());

        assertThat(stubConnectionRepository.findConnectionIdByAddress(ADDRESS)).isEmpty();
    }

    @Test
    void CompleteLink_NoServiceToken_ShouldReturnUnauthorized() throws Exception {
        // The connector is reachable only by the provider's service; without this the token
        // alone would be enough for anyone who can reach the router
        String token = issueLinkToken(accessTokenOf("notoken@example.com"));

        mockMvc.perform(jsonPost(CONNECTOR + "/linked",
                        "{\"token\":\"%s\",\"destination\":{\"address\":\"%s\"}}".formatted(token, ADDRESS)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void CompleteLink_WrongServiceToken_ShouldReturnUnauthorized() throws Exception {
        String token = issueLinkToken(accessTokenOf("badtoken@example.com"));

        mockMvc.perform(jsonPost(CONNECTOR + "/linked",
                        "{\"token\":\"%s\",\"destination\":{\"address\":\"%s\"}}".formatted(token, ADDRESS))
                        .header("X-Service-Token", "not-the-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void CompleteLink_MissingAddress_ShouldReturnBadRequestAndKeepTheTokenUsable() throws Exception {
        String token = issueLinkToken(accessTokenOf("noaddress@example.com"));

        mockMvc.perform(signalLinkedWithDestination(token, "{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(signalLinkedWithDestination(token, "{\"address\":\"  \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(signalLinked(token, ADDRESS))
                .andExpect(status().isOk());
    }

    @Test
    void CompleteLink_DestinationLinkedToAnotherUser_ShouldReturnConflict() throws Exception {
        linkedConnectionId(accessTokenOf("first@example.com"), ADDRESS);
        String otherToken = issueLinkToken(accessTokenOf("second@example.com"));

        mockMvc.perform(signalLinked(otherToken, ADDRESS))
                .andExpect(status().isConflict());

        // The destination must still belong to whoever linked it first
        assertThat(connectionOfAddress(ADDRESS).getUser().getEmail()).isEqualTo("first@example.com");
    }

    @Test
    void CompleteLink_SameDestinationAndSameAccountTwice_ShouldReturnTheExistingConnection() throws Exception {
        // Linking the same destination again is a user mistake, not an error
        String accessToken = accessTokenOf("relink@example.com");

        long first = linkedConnectionId(accessToken, ADDRESS);
        long second = linkedConnectionId(accessToken, ADDRESS);

        assertThat(second).isEqualTo(first);
        assertThat(connectionRepository.findAll()).hasSize(1);
    }

    @Test
    void CompleteLink_TitleReported_ShouldUseItAsTheConnectionTitle() throws Exception {
        // A provider's service may report a name, such as a group chat's, which beats no title
        String token = issueLinkToken(accessTokenOf("group@example.com"));

        mockMvc.perform(jsonPost(CONNECTOR + "/linked",
                        "{\"token\":\"%s\",\"title\":\" Team chat \",\"destination\":{\"address\":\"stub-group\"}}"
                                .formatted(token))
                        .header("X-Service-Token", serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Team chat"))
                .andExpect(jsonPath("$.address").value("stub-group"));
    }

    @Test
    void CompleteLink_TokenOfAnotherProvidersPath_ShouldReturnUnauthorized() throws Exception {
        // A session belongs to one provider; its token is no proof on another provider's path
        String token = issueLinkToken(accessTokenOf("otherpath@example.com"));

        mockMvc.perform(jsonPost("/connector/999/linked",
                        "{\"token\":\"%s\",\"destination\":{\"address\":\"%s\"}}".formatted(token, ADDRESS))
                        .header("X-Service-Token", serviceToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void GetStatus_UnknownProvider_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/connector/999/status")
                        .param("address", ADDRESS)
                        .header("X-Service-Token", serviceToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void GetLinkSession_SessionOfAnotherUser_ShouldReturnNotFound() throws Exception {
        long strangersSessionId = startStubLink(accessTokenOf("session-owner@example.com")).get("id").asLong();

        mockMvc.perform(get("/me/link-sessions/" + strangersSessionId)
                        .header("Authorization", "Bearer " + accessTokenOf("session-thief@example.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void GetConnections_Authenticated_ShouldReturnOnlyTheCallersConnections() throws Exception {
        String ownerToken = accessTokenOf("owner@example.com");
        linkedConnectionId(ownerToken, ADDRESS);
        linkedConnectionId(accessTokenOf("stranger@example.com"), "stub-999000111");

        mockMvc.perform(get("/me/connections").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].address").value(ADDRESS));
    }

    @Test
    void GetConnections_NoAuthentication_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/me/connections"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void RenameConnection_OwnConnection_ShouldUpdateTheDisplayTitle() throws Exception {
        String accessToken = accessTokenOf("rename@example.com");
        long connectionId = linkedConnectionId(accessToken, ADDRESS);

        mockMvc.perform(patch("/me/connections/" + connectionId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Work laptop\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Work laptop"))
                .andExpect(jsonPath("$.address").value(ADDRESS));
    }

    @Test
    void RenameConnection_ConnectionOwnedByAnotherUser_ShouldReturnNotFound() throws Exception {
        long strangersConnectionId = linkedConnectionId(accessTokenOf("victim@example.com"), ADDRESS);
        String attackerToken = accessTokenOf("attacker@example.com");

        mockMvc.perform(patch("/me/connections/" + strangersConnectionId)
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"hijacked\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void DeleteConnection_OwnConnection_ShouldRemoveItAndItsDetails() throws Exception {
        String accessToken = accessTokenOf("unlink@example.com");
        long connectionId = linkedConnectionId(accessToken, ADDRESS);

        mockMvc.perform(delete("/me/connections/" + connectionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        assertThat(connectionRepository.findById(connectionId)).isEmpty();
        assertThat(stubConnectionRepository.findConnectionIdByAddress(ADDRESS)).isEmpty();
    }

    @Test
    void DeleteConnection_ConnectionOwnedByAnotherUser_ShouldReturnNotFound() throws Exception {
        // 404 rather than 403: a 403 would confirm the id exists, making ids enumerable
        long strangersConnectionId = linkedConnectionId(accessTokenOf("keepme@example.com"), ADDRESS);
        String attackerToken = accessTokenOf("thief@example.com");

        mockMvc.perform(delete("/me/connections/" + strangersConnectionId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());

        assertThat(connectionRepository.findById(strangersConnectionId)).isPresent();
    }

    @Test
    void UnlinkDestination_LinkedDestination_ShouldRemoveTheConnection() throws Exception {
        long connectionId = linkedConnectionId(accessTokenOf("botunlink@example.com"), ADDRESS);

        mockMvc.perform(jsonPost(CONNECTOR + "/unlinked",
                        "{\"destination\":{\"address\":\"%s\"}}".formatted(ADDRESS))
                        .header("X-Service-Token", serviceToken))
                .andExpect(status().isOk());

        assertThat(connectionRepository.findById(connectionId)).isEmpty();
    }

    @Test
    void UnlinkDestination_DestinationThatWasNeverLinked_ShouldSucceed() throws Exception {
        // Unlinking an unlinked destination is already the desired state, not an error
        mockMvc.perform(jsonPost(CONNECTOR + "/unlinked", "{\"destination\":{\"address\":\"stub-123456\"}}")
                        .header("X-Service-Token", serviceToken))
                .andExpect(status().isOk());
    }

    @Test
    void GetStatus_LinkedDestination_ShouldReportTheOwningAccount() throws Exception {
        linkedConnectionId(accessTokenOf("status@example.com"), ADDRESS);

        mockMvc.perform(get(CONNECTOR + "/status")
                        .param("address", ADDRESS)
                        .header("X-Service-Token", serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.email").value("status@example.com"));
    }

    @Test
    void GetStatus_UnlinkedDestination_ShouldReportNotLinked() throws Exception {
        mockMvc.perform(get(CONNECTOR + "/status")
                        .param("address", "stub-5550000")
                        .header("X-Service-Token", serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(false))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void GetStatus_NoServiceToken_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get(CONNECTOR + "/status").param("address", ADDRESS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void GetStatus_UserAccessTokenInsteadOfServiceToken_ShouldReturnForbidden() throws Exception {
        // A logged-in user is authenticated but is not the service; the connector is not
        // part of the browser-facing API
        String accessToken = accessTokenOf("notaservice@example.com");

        mockMvc.perform(get(CONNECTOR + "/status")
                        .param("address", ADDRESS)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void DeleteAccount_UserWithAConnection_ShouldCascadeToItsConnectionAndDetails() throws Exception {
        // connection.user_id and stub_connection's foreign key are both ON DELETE
        // CASCADE, so removing an account must not leave its destinations behind
        linkedConnectionId(accessTokenOf("cascade@example.com"), ADDRESS);

        userRepository.deleteAll();

        assertThat(connectionRepository.findAll()).isEmpty();
        assertThat(stubConnectionRepository.findConnectionIdByAddress(ADDRESS)).isEmpty();
    }
}
