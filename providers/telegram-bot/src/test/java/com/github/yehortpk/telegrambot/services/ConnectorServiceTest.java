package com.github.yehortpk.telegrambot.services;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.github.yehortpk.telegrambot.models.ConnectorStatusDTO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ConnectorServiceTest {
    private static final String CONNECTOR_URL = "http://router/connector/1";

    private ConnectorService createService() {
        ConnectorService connectorService = new ConnectorService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(connectorService, "connectorURL", CONNECTOR_URL);
        ReflectionTestUtils.setField(connectorService, "serviceToken", "service-token");
        return connectorService;
    }

    private MockRestServiceServer bindServer(ConnectorService connectorService) {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(connectorService, "restTemplate");
        return MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void LinkTelegramConnection_ValidRequest_ShouldPostLinkedSignalWithServiceToken() {
        ConnectorService connectorService = createService();
        MockRestServiceServer server = bindServer(connectorService);
        server.expect(requestTo(CONNECTOR_URL + "/linked"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Token", "service-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                        "{\"token\":\"link-token\",\"title\":\"Job hunt\",\"destination\":{\"chatId\":\"7000000000\"}}",
                        true))
                .andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));

        connectorService.linkTelegramConnection(7_000_000_000L, "Job hunt", "link-token");

        server.verify();
    }

    @Test
    void LinkTelegramConnection_RouterRejectsToken_ShouldThrowUnauthorized() {
        ConnectorService connectorService = createService();
        MockRestServiceServer server = bindServer(connectorService);
        server.expect(requestTo(CONNECTOR_URL + "/linked")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(HttpClientErrorException.Unauthorized.class,
                () -> connectorService.linkTelegramConnection(1L, null, "link-token"));
    }

    @Test
    void UnlinkTelegramConnection_ValidRequest_ShouldPostUnlinkedSignalWithServiceToken() {
        ConnectorService connectorService = createService();
        MockRestServiceServer server = bindServer(connectorService);
        server.expect(requestTo(CONNECTOR_URL + "/unlinked"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Token", "service-token"))
                .andExpect(content().json("{\"destination\":{\"chatId\":\"-1001234\"}}", true))
                .andRespond(withSuccess());

        connectorService.unlinkTelegramConnection(-1001234L);

        server.verify();
    }

    @Test
    void FindTelegramStatus_LinkedChat_ShouldReadTheRoutersStatus() {
        ConnectorService connectorService = createService();
        MockRestServiceServer server = bindServer(connectorService);
        server.expect(requestTo(CONNECTOR_URL + "/status?chatId=473123822"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Service-Token", "service-token"))
                .andRespond(withSuccess(
                        "{\"linked\":true,\"email\":\"me@example.com\",\"filterCount\":3,\"activeFilterCount\":2}",
                        MediaType.APPLICATION_JSON));

        ConnectorStatusDTO status = connectorService.findTelegramStatus(473123822L);

        assertEquals(new ConnectorStatusDTO(true, "me@example.com", 3, 2), status);
        server.verify();
    }

    @Test
    void FindTelegramStatus_NegativeGroupChatId_ShouldPassItUnchanged() {
        ConnectorService connectorService = createService();
        MockRestServiceServer server = bindServer(connectorService);
        server.expect(requestTo(CONNECTOR_URL + "/status?chatId=-1001234"))
                .andRespond(withSuccess("{\"linked\":false}", MediaType.APPLICATION_JSON));

        ConnectorStatusDTO status = connectorService.findTelegramStatus(-1001234L);

        assertEquals(new ConnectorStatusDTO(false, null, 0, 0), status);
    }
}
