package com.github.yehortpk.dashboard.config;

import com.github.yehortpk.dashboard.models.vacancy.VacanciesPageDTO;
import com.github.yehortpk.dashboard.models.vacancy.Vacancy;
import com.github.yehortpk.dashboard.models.vacancy.VacancyCompanyDTO;
import com.github.yehortpk.dashboard.models.vacancy.VacancyDetailsDTO;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelMapperConfigTest {
    private static final LocalDateTime PARSED_AT = LocalDateTime.of(2026, 11, 11, 9, 30);

    private final ModelMapper modelMapper = new ModelMapperConfig().modelMapper();

    private Vacancy createVacancy() {
        Vacancy vacancy = new Vacancy();
        vacancy.setTitle("Java Developer");
        vacancy.setLink("https://acme.example/1");
        vacancy.setParsedAt(PARSED_AT);
        vacancy.setCompanyId(7);
        vacancy.setCompanyTitle("Acme");
        vacancy.setCompanyUrl("https://acme.example");
        return vacancy;
    }

    @Test
    void MapVacancy_ParsedAtWithoutZone_ShouldCarryTheDashboardOffset() {
        VacancyCompanyDTO dto = modelMapper.map(createVacancy(), VacancyCompanyDTO.class);

        assertThat(dto.getParsedAt()).isEqualTo(PARSED_AT.atZone(ZoneId.systemDefault()).toOffsetDateTime());
        assertThat(dto.getURL()).isEqualTo("https://acme.example/1");
    }

    @Test
    void MapVacancy_RemoteVacancy_ShouldCarryRemoteFlag() {
        Vacancy vacancy = createVacancy();
        vacancy.setRemote(true);

        VacancyCompanyDTO dto = modelMapper.map(vacancy, VacancyCompanyDTO.class);

        assertThat(dto.isRemote()).isTrue();
    }

    @Test
    void MapVacancy_CompanyColumns_ShouldBecomeTheCompany() {
        VacancyCompanyDTO dto = modelMapper.map(createVacancy(), VacancyCompanyDTO.class);

        assertThat(dto.getCompany().getCompanyId()).isEqualTo(7);
        assertThat(dto.getCompany().getCompanyTitle()).isEqualTo("Acme");
        assertThat(dto.getCompany().getCompanyURL()).isEqualTo("https://acme.example");
    }

    @Test
    void MapVacanciesPage_ParsedAtWithoutZone_ShouldCarryTheDashboardOffset() {
        VacanciesPageDTO page = modelMapper.getTypeMap(org.springframework.data.domain.Page.class, VacanciesPageDTO.class)
                .map(new PageImpl<>(List.of(createVacancy())));

        assertThat(page.getVacancies()).hasSize(1);
        assertThat(page.getVacancies().getFirst().getParsedAt())
                .isEqualTo(PARSED_AT.atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }

    @Test
    void MapVacancy_ToDetails_ShouldCarryIdLinkAndDescription() {
        Vacancy vacancy = createVacancy();
        vacancy.setVacancyID(42);
        vacancy.setDescription("Build things.");

        VacancyDetailsDTO dto = modelMapper.map(vacancy, VacancyDetailsDTO.class);

        assertThat(dto.getId()).isEqualTo(42);
        assertThat(dto.getURL()).isEqualTo("https://acme.example/1");
        assertThat(dto.getDescription()).isEqualTo("Build things.");
        assertThat(dto.getCompany().getCompanyTitle()).isEqualTo("Acme");
        assertThat(dto.getCompany().getCompanyURL()).isEqualTo("https://acme.example");
    }
}
