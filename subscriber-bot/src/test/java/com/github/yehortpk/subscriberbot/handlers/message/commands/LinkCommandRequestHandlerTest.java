package com.github.yehortpk.subscriberbot.handlers.message.commands;

import com.github.yehortpk.subscriberbot.exceptions.TelegramRuntimeException;
import com.github.yehortpk.subscriberbot.models.UserRequestDTO;
import com.github.yehortpk.subscriberbot.services.ConnectorService;
import com.github.yehortpk.subscriberbot.utils.TelegramServiceUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LinkCommandRequestHandlerTest {
    private static final long CHAT_ID = 7_000_000_000L;
    private static final int MESSAGE_ID = 42;
    private static final String LINK_TOKEN = "Zm9vYmFyLXRva2Vu";
    private static final String GROUP_TITLE = "Job hunt";

    @Mock
    private ConnectorService connectorService;
    @Mock
    private TelegramServiceUtil telegramServiceUtil;
    @InjectMocks
    private LinkCommandRequestHandler handler;

    private UserRequestDTO createRequest(String text) {
        Message message = new Message();
        message.setMessageId(MESSAGE_ID);
        message.setText(text);

        Update update = new Update();
        update.setMessage(message);

        return new UserRequestDTO(CHAT_ID, update);
    }

    @BeforeEach
    void injectTelegramServiceUtil() {
        ReflectionTestUtils.setField(handler, "telegramServiceUtil", telegramServiceUtil);
    }

    @Test
    void IsApplicable_CommandWithCode_ShouldReturnTrue() {
        assertTrue(handler.isApplicable(createRequest("/link " + LINK_TOKEN)));
    }

    @Test
    void IsApplicable_CommandWithoutCode_ShouldReturnTrue() {
        assertTrue(handler.isApplicable(createRequest("/link")));
    }

    @Test
    void IsApplicable_CodeOnNextLine_ShouldReturnTrue() {
        assertTrue(handler.isApplicable(createRequest("/link\n" + LINK_TOKEN)));
    }

    @Test
    void IsApplicable_OtherCommandWithSamePrefix_ShouldReturnFalse() {
        assertFalse(handler.isApplicable(createRequest("/linked")));
    }

    @Test
    void IsApplicable_MessageWithoutText_ShouldReturnFalse() {
        assertFalse(handler.isApplicable(createRequest(null)));
    }

    @Test
    void HandleRequest_ValidCode_ShouldLinkChatAndDeleteMessage() {
        SendMessage reply = handler.handleRequest(createRequest("/link  " + LINK_TOKEN));

        verify(connectorService).linkTelegramConnection(CHAT_ID, null, LINK_TOKEN);
        verify(telegramServiceUtil).deleteMessage(CHAT_ID, MESSAGE_ID);
        assertEquals("This chat is now linked to your account.", reply.getText());
    }

    @Test
    void HandleRequest_MissingCode_ShouldAskForCodeWithoutLinking() {
        SendMessage reply = handler.handleRequest(createRequest("/link"));

        verify(connectorService, never()).linkTelegramConnection(anyLong(), any(), anyString());
        verify(telegramServiceUtil, never()).deleteMessage(anyLong(), anyInt());
        assertTrue(reply.getText().contains("/link your-code"));
    }

    @Test
    void HandleRequest_InvalidCode_ShouldReportItAndDeleteMessage() {
        doThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "", null, null, null))
                .when(connectorService).linkTelegramConnection(CHAT_ID, null, LINK_TOKEN);

        SendMessage reply = handler.handleRequest(createRequest("/link " + LINK_TOKEN));

        verify(telegramServiceUtil).deleteMessage(CHAT_ID, MESSAGE_ID);
        assertTrue(reply.getText().contains("not valid"));
    }

    @Test
    void HandleRequest_ChatHeldByAnotherAccount_ShouldReportConflict() {
        doThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "", null, null, null))
                .when(connectorService).linkTelegramConnection(CHAT_ID, null, LINK_TOKEN);

        SendMessage reply = handler.handleRequest(createRequest("/link " + LINK_TOKEN));

        assertTrue(reply.getText().contains("already linked to another account"));
    }

    @Test
    void HandleRequest_RouterUnreachable_ShouldAskToRetry() {
        doThrow(new ResourceAccessException("connection refused"))
                .when(connectorService).linkTelegramConnection(CHAT_ID, null, LINK_TOKEN);

        SendMessage reply = handler.handleRequest(createRequest("/link " + LINK_TOKEN));

        assertTrue(reply.getText().contains("try again later"));
    }

    @Test
    void HandleRequest_MessageCannotBeDeleted_ShouldStillLinkChat() {
        doThrow(new TelegramRuntimeException("message can't be deleted"))
                .when(telegramServiceUtil).deleteMessage(CHAT_ID, MESSAGE_ID);

        SendMessage reply = handler.handleRequest(createRequest("/link " + LINK_TOKEN));

        verify(connectorService).linkTelegramConnection(CHAT_ID, null, LINK_TOKEN);
        assertEquals("This chat is now linked to your account.", reply.getText());
    }

    @Test
    void HandleRequest_GroupChat_ShouldReportTheGroupTitle() {
        UserRequestDTO request = createRequest("/link " + LINK_TOKEN);
        Chat chat = new Chat();
        chat.setTitle(GROUP_TITLE);
        request.getUpdate().getMessage().setChat(chat);

        handler.handleRequest(request);

        verify(connectorService).linkTelegramConnection(CHAT_ID, GROUP_TITLE, LINK_TOKEN);
    }

    @Test
    void IsApplicable_CommandAddressedToTheBotInAGroup_ShouldReturnTrue() {
        // In a group with several bots Telegram sends /link@BotName
        assertTrue(handler.isApplicable(createRequest("/link@VacancyVaultBot " + LINK_TOKEN)));
    }

    @Test
    void HandleRequest_CommandAddressedToTheBotInAGroup_ShouldLinkWithTheCode() {
        handler.handleRequest(createRequest("/link@VacancyVaultBot " + LINK_TOKEN));

        verify(connectorService).linkTelegramConnection(CHAT_ID, null, LINK_TOKEN);
    }
}
