package com.github.yehortpk.router.listener;

import com.github.yehortpk.router.PostgresContainerTest;
import com.github.yehortpk.router.models.vacancy.NewVacancyDTO;
import com.github.yehortpk.router.repositories.NotifiedVacancyRepository;
import com.github.yehortpk.router.services.NotifierService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The router notifies about a published vacancy once per link, over the migrated
 * {@code notified_vacancy} table. No broker runs: the listener method is called the way its
 * container would call it, and matching is replaced, since only whether it runs is under test.
 */
@SpringBootTest
class NewVacancyListenerTest extends PostgresContainerTest {
    @Autowired
    private NewVacancyListener newVacancyListener;

    @Autowired
    private NotifiedVacancyRepository notifiedVacancyRepository;

    @MockitoBean
    private NotifierService notifierService;

    @BeforeEach
    void clearBefore() {
        notifiedVacancyRepository.deleteAll();
    }

    @AfterEach
    void clearAfter() {
        notifiedVacancyRepository.deleteAll();
    }

    private static NewVacancyDTO createVacancy(String link) {
        return NewVacancyDTO.builder()
                .link(link)
                .title("Java Developer")
                .company(new NewVacancyDTO.Company(1, "Acme", "https://acme.example"))
                .build();
    }

    @Test
    void ListenNewVacanciesTopic_NewLink_ShouldRecordItAndNotify() {
        NewVacancyDTO vacancy = createVacancy("https://acme.example/1");

        newVacancyListener.listenNewVacanciesTopic(vacancy);

        verify(notifierService).notifyUsers(vacancy);
        assertThat(notifiedVacancyRepository.findById("https://acme.example/1")).isPresent();
    }

    @Test
    void ListenNewVacanciesTopic_SameLinkTwice_ShouldNotifyOnce() {
        // Redelivered by Kafka, republished by the parser after a crash, or replayed from the
        // start of the topic: every chat would otherwise get the vacancy again
        newVacancyListener.listenNewVacanciesTopic(createVacancy("https://acme.example/1"));
        newVacancyListener.listenNewVacanciesTopic(createVacancy("https://acme.example/1"));

        verify(notifierService, times(1)).notifyUsers(any());
        assertThat(notifiedVacancyRepository.count()).isEqualTo(1);
    }

    @Test
    void ListenNewVacanciesTopic_DifferentLinks_ShouldNotifyAboutEach() {
        newVacancyListener.listenNewVacanciesTopic(createVacancy("https://acme.example/1"));
        newVacancyListener.listenNewVacanciesTopic(createVacancy("https://acme.example/2"));

        verify(notifierService, times(2)).notifyUsers(any());
        assertThat(notifiedVacancyRepository.count()).isEqualTo(2);
    }
}
