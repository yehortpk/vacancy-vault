package com.github.yehortpk.router.services;

import com.github.yehortpk.router.PostgresContainerTest;
import com.github.yehortpk.router.models.connection.Connection;
import com.github.yehortpk.router.models.filter.Filter;
import com.github.yehortpk.router.models.user.AppUser;
import com.github.yehortpk.router.models.user.UserRole;
import com.github.yehortpk.router.models.vacancy.VacancyDTO;
import com.github.yehortpk.router.providers.telegram.TelegramNotificationDTO;
import com.github.yehortpk.router.providers.stub.StubConnectionProvider;
import com.github.yehortpk.router.providers.telegram.TelegramConnection;
import com.github.yehortpk.router.providers.telegram.TelegramConnectionProvider;
import com.github.yehortpk.router.providers.telegram.TelegramConnectionRepository;
import com.github.yehortpk.router.repositories.ConnectionRepository;
import com.github.yehortpk.router.repositories.FilterRepository;
import com.github.yehortpk.router.repositories.ProviderRepository;
import com.github.yehortpk.router.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/**
 * Matching a new vacancy against the stored filters, over the migrated schema: only bound
 * filters take part, every matching account is notified, and one destination gets a vacancy
 * at most once. Kafka is replaced, so what would be published is what is asserted; the test-only
 * stub provider is spied on to check that each provider gets only its own connections.
 */
@SpringBootTest
class NotifierServiceTest extends PostgresContainerTest {
    @Autowired
    private NotifierService notifierService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private TelegramConnectionRepository telegramConnectionRepository;

    @Autowired
    private FilterRepository filterRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private KafkaTemplate<String, TelegramNotificationDTO> kafkaTemplate;

    @MockitoSpyBean
    private StubConnectionProvider stubConnectionProvider;

    @BeforeEach
    void clearBefore() {
        userRepository.deleteAll();
    }

    @AfterEach
    void clearAfter() {
        userRepository.deleteAll();
    }

    private AppUser createUser(String email) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash("not-a-real-hash");
        user.setCreatedAt(LocalDateTime.now());
        user.setEnabled(true);
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }

    /**
     * Both rows in one transaction, as the link flow writes them: the details row's
     * {@code @MapsId} needs its connection managed, not detached.
     */
    private Connection createTelegramConnection(AppUser user, long chatId) {
        return transactionTemplate.execute(status -> {
            Connection connection = new Connection();
            connection.setUser(user);
            connection.setProvider(providerRepository.findById(TelegramConnectionProvider.PROVIDER_ID).orElseThrow());
            connection.setLinkedAt(LocalDateTime.now());
            connectionRepository.save(connection);

            TelegramConnection telegramConnection = new TelegramConnection();
            telegramConnection.setConnection(connection);
            telegramConnection.setChatId(chatId);
            telegramConnectionRepository.save(telegramConnection);
            return connection;
        });
    }

    /** A stub connection without its details row, which the spied stub never reads. */
    private Connection createStubConnection(AppUser user) {
        Connection connection = new Connection();
        connection.setUser(user);
        connection.setProvider(providerRepository.findById(StubConnectionProvider.PROVIDER_ID).orElseThrow());
        connection.setLinkedAt(LocalDateTime.now());
        return connectionRepository.save(connection);
    }

    private Filter createFilter(AppUser user, String expression, Connection... connections) {
        Filter filter = new Filter();
        filter.setUser(user);
        filter.setTitle(expression);
        filter.setFilter(expression);
        filter.setConnections(new HashSet<>(Set.of(connections)));
        return filterRepository.save(filter);
    }

    private static VacancyDTO createVacancy(String companyTitle, String title) {
        return VacancyDTO.builder()
                .companyTitle(companyTitle)
                .title(title)
                .link("https://example.com/" + title.replace(' ', '-'))
                .build();
    }

    private ArgumentCaptor<TelegramNotificationDTO> captureNotifications(int count) {
        ArgumentCaptor<TelegramNotificationDTO> notifications = ArgumentCaptor.forClass(TelegramNotificationDTO.class);
        verify(kafkaTemplate, times(count)).sendDefault(notifications.capture());
        return notifications;
    }

    @Test
    void NotifyUsers_VacancyMatchesFiltersOfTwoUsers_ShouldNotifyBoth() {
        // The old global "already notified" set let the first matching filter silence everyone else's
        AppUser first = createUser("first@example.com");
        AppUser second = createUser("second@example.com");
        createFilter(first, "java", createTelegramConnection(first, 111));
        createFilter(second, "developer", createTelegramConnection(second, -222));

        notifierService.notifyUsers(createVacancy("Acme", "Java Developer"));

        assertThat(captureNotifications(2).getAllValues())
                .extracting(TelegramNotificationDTO::getChatId)
                .containsExactlyInAnyOrder(111L, -222L);
    }

    @Test
    void NotifyUsers_FilterWithoutBinding_ShouldNotNotify() {
        // A draft takes no part in matching
        createFilter(createUser("draft@example.com"), "java");

        notifierService.notifyUsers(createVacancy("Acme", "Java Developer"));

        verify(kafkaTemplate, never()).sendDefault(any());
    }

    @Test
    void NotifyUsers_UserWithTwoMatchingFiltersOnOneConnection_ShouldNotifyOnce() {
        AppUser user = createUser("twice@example.com");
        Connection connection = createTelegramConnection(user, 333);
        createFilter(user, "java", connection);
        createFilter(user, "developer", connection);

        notifierService.notifyUsers(createVacancy("Acme", "Java Developer"));

        assertThat(captureNotifications(1).getValue().getChatId()).isEqualTo(333L);
    }

    @Test
    void NotifyUsers_FilterBoundToTwoConnections_ShouldNotifyEach() {
        AppUser user = createUser("fanout@example.com");
        createFilter(user, "java", createTelegramConnection(user, 444), createTelegramConnection(user, 555));

        notifierService.notifyUsers(createVacancy("Acme", "Java Developer"));

        assertThat(captureNotifications(2).getAllValues())
                .extracting(TelegramNotificationDTO::getChatId)
                .containsExactlyInAnyOrder(444L, 555L);
    }

    @Test
    void NotifyUsers_VacancyMatchingNoFilter_ShouldNotNotify() {
        AppUser user = createUser("python@example.com");
        createFilter(user, "python", createTelegramConnection(user, 666));

        notifierService.notifyUsers(createVacancy("Acme", "Java Developer"));

        verify(kafkaTemplate, never()).sendDefault(any());
    }

    @Test
    void NotifyUsers_CompanyKeywordFilter_ShouldMatchOnlyThatCompany() {
        // The company keyword is matched against the company title, as in the vacancies view
        AppUser user = createUser("acme@example.com");
        createFilter(user, "Acme, java", createTelegramConnection(user, 777));

        notifierService.notifyUsers(createVacancy("Globex", "Java Developer"));
        notifierService.notifyUsers(createVacancy("Acme", "Java Developer"));

        TelegramNotificationDTO notification = captureNotifications(1).getValue();
        assertThat(notification.getCompanyTitle()).isEqualTo("Acme");
        assertThat(notification.getFilter()).isEqualTo("Acme, java");
    }

    @Test
    void NotifyUsers_FilterBoundToConnectionsOfTwoProviders_ShouldNotifyEachThroughItsProvider() {
        AppUser user = createUser("mixed@example.com");
        Connection stubConnection = createStubConnection(user);
        Filter filter = createFilter(user, "java", createTelegramConnection(user, 888), stubConnection);
        VacancyDTO vacancy = createVacancy("Acme", "Java Developer");

        notifierService.notifyUsers(vacancy);

        assertThat(captureNotifications(1).getValue().getChatId()).isEqualTo(888L);
        verify(stubConnectionProvider).notifyConnections(
                argThat(matched -> matched.keySet().equals(Set.of(stubConnection.getId()))
                        && matched.get(stubConnection.getId()).getId() == filter.getId()),
                eq(vacancy));
    }

    @Test
    void NotifyUsers_OneProviderFails_ShouldStillNotifyTheOthers() {
        AppUser user = createUser("failing@example.com");
        createFilter(user, "java", createTelegramConnection(user, 999), createStubConnection(user));
        doThrow(new IllegalStateException("stub is down"))
                .when(stubConnectionProvider).notifyConnections(anyMap(), any());

        notifierService.notifyUsers(createVacancy("Acme", "Java Developer"));

        assertThat(captureNotifications(1).getValue().getChatId()).isEqualTo(999L);
    }
}
