package com.github.yehortpk.dashboard.listener;

import com.github.yehortpk.dashboard.PostgresContainerTest;
import com.github.yehortpk.dashboard.models.vacancy.NewVacancyDTO;
import com.github.yehortpk.dashboard.models.vacancy.Vacancy;
import com.github.yehortpk.dashboard.repositories.VacancyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the two listeners do to the stored vacancies, over the migrated schema. No broker runs:
 * the listener methods are called the way their containers would call them.
 */
@SpringBootTest
class VacancyListenerTest extends PostgresContainerTest {
    private static final int ACME_ID = 1;
    private static final int GLOBEX_ID = 2;

    @Autowired
    private NewVacancyListener newVacancyListener;

    @Autowired
    private RemovedVacancyListener removedVacancyListener;

    @Autowired
    private VacancyRepository vacancyRepository;

    @BeforeEach
    void clearBefore() {
        vacancyRepository.deleteAll();
    }

    @AfterEach
    void clearAfter() {
        vacancyRepository.deleteAll();
    }

    private static NewVacancyDTO createVacancy(String link, int companyId, String companyTitle, String companyUrl) {
        return NewVacancyDTO.builder()
                .link(link)
                .title("Java Developer")
                .description("Build things.")
                .minSalary(1000)
                .salaryCurrency("USD")
                .salaryPeriod("MONTH")
                .isRemote(true)
                .parsedAt(LocalDateTime.of(2026, 11, 11, 9, 30))
                .company(new NewVacancyDTO.Company(companyId, companyTitle, companyUrl))
                .build();
    }

    private Vacancy findVacancy(String link) {
        return vacancyRepository.findAll().stream()
                .filter(vacancy -> vacancy.getLink().equals(link))
                .findFirst().orElseThrow();
    }

    @Test
    void ListenNewVacanciesTopic_NewLink_ShouldStoreTheVacancyWithItsCompany() {
        newVacancyListener.listenNewVacanciesTopic(
                createVacancy("https://acme.example/1", ACME_ID, "Acme", "https://acme.example"));

        Vacancy stored = findVacancy("https://acme.example/1");
        assertThat(stored.getTitle()).isEqualTo("Java Developer");
        assertThat(stored.getDescription()).isEqualTo("Build things.");
        assertThat(stored.getMinSalary()).isEqualTo(1000);
        assertThat(stored.getMaxSalary()).isNull();
        assertThat(stored.getSalaryCurrency()).isEqualTo("USD");
        assertThat(stored.isRemote()).isTrue();
        assertThat(stored.getParsedAt()).isEqualTo(LocalDateTime.of(2026, 11, 11, 9, 30));
        assertThat(stored.getCompanyId()).isEqualTo(ACME_ID);
        assertThat(stored.getCompanyTitle()).isEqualTo("Acme");
        assertThat(stored.getCompanyUrl()).isEqualTo("https://acme.example");
    }

    @Test
    void ListenNewVacanciesTopic_SameVacancyTwice_ShouldStoreOneRow() {
        // Kafka delivers at least once, and the parser republishes after a crash
        NewVacancyDTO vacancy = createVacancy("https://acme.example/1", ACME_ID, "Acme", "https://acme.example");

        newVacancyListener.listenNewVacanciesTopic(vacancy);
        newVacancyListener.listenNewVacanciesTopic(vacancy);

        assertThat(vacancyRepository.count()).isEqualTo(1);
    }

    @Test
    void ListenNewVacanciesTopic_CompanyTitleChanged_ShouldUpdateItsStoredVacancies() {
        newVacancyListener.listenNewVacanciesTopic(
                createVacancy("https://acme.example/1", ACME_ID, "Acme", "https://acme.example"));
        newVacancyListener.listenNewVacanciesTopic(
                createVacancy("https://globex.example/1", GLOBEX_ID, "Globex", "https://globex.example"));

        newVacancyListener.listenNewVacanciesTopic(
                createVacancy("https://acme.example/2", ACME_ID, "Acme Corp", "https://acme.example"));

        assertThat(List.of(findVacancy("https://acme.example/1"), findVacancy("https://acme.example/2")))
                .extracting(Vacancy::getCompanyTitle).containsOnly("Acme Corp");
        // Another company's rows are not touched
        assertThat(findVacancy("https://globex.example/1").getCompanyTitle()).isEqualTo("Globex");
    }

    @Test
    void ListenNewVacanciesTopic_CompanyUrlChangedOnRedelivery_ShouldUpdateItsStoredVacancies() {
        // The company columns follow the latest message even when the vacancy itself is skipped
        newVacancyListener.listenNewVacanciesTopic(
                createVacancy("https://acme.example/1", ACME_ID, "Acme", null));

        newVacancyListener.listenNewVacanciesTopic(
                createVacancy("https://acme.example/1", ACME_ID, "Acme", "https://acme.example"));

        assertThat(vacancyRepository.count()).isEqualTo(1);
        assertThat(findVacancy("https://acme.example/1").getCompanyUrl()).isEqualTo("https://acme.example");
    }

    @Test
    void ListenRemovedVacanciesTopic_StoredLink_ShouldDeleteOnlyThatVacancy() {
        newVacancyListener.listenNewVacanciesTopic(
                createVacancy("https://acme.example/1", ACME_ID, "Acme", "https://acme.example"));
        newVacancyListener.listenNewVacanciesTopic(
                createVacancy("https://acme.example/2", ACME_ID, "Acme", "https://acme.example"));

        removedVacancyListener.listenRemovedVacanciesTopic("https://acme.example/1");

        assertThat(vacancyRepository.findAll()).extracting(Vacancy::getLink)
                .containsExactly("https://acme.example/2");
    }

    @Test
    void ListenRemovedVacanciesTopic_UnknownLink_ShouldDoNothing() {
        newVacancyListener.listenNewVacanciesTopic(
                createVacancy("https://acme.example/1", ACME_ID, "Acme", "https://acme.example"));

        removedVacancyListener.listenRemovedVacanciesTopic("https://acme.example/never-stored");

        assertThat(vacancyRepository.count()).isEqualTo(1);
    }
}
