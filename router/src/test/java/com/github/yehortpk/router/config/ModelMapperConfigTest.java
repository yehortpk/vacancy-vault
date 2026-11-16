package com.github.yehortpk.router.config;

import com.github.yehortpk.router.models.company.Company;
import com.github.yehortpk.router.models.vacancy.VacanciesPageDTO;
import com.github.yehortpk.router.models.vacancy.Vacancy;
import com.github.yehortpk.router.models.vacancy.VacancyCompanyDTO;
import com.github.yehortpk.router.models.vacancy.VacancyDTO;
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
        Company company = new Company();
        company.setTitle("Acme");

        Vacancy vacancy = new Vacancy();
        vacancy.setTitle("Java Developer");
        vacancy.setLink("https://acme.example/1");
        vacancy.setParsedAt(PARSED_AT);
        vacancy.setCompany(company);
        return vacancy;
    }

    @Test
    void MapVacancy_ParsedAtWithoutZone_ShouldCarryTheRouterOffset() {
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
    void MapVacanciesPage_ParsedAtWithoutZone_ShouldCarryTheRouterOffset() {
        VacanciesPageDTO page = modelMapper.getTypeMap(org.springframework.data.domain.Page.class, VacanciesPageDTO.class)
                .map(new PageImpl<>(List.of(createVacancy())));

        assertThat(page.getVacancies()).hasSize(1);
        assertThat(page.getVacancies().getFirst().getParsedAt())
                .isEqualTo(PARSED_AT.atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }

    @Test
    void MapVacancyDTO_WithDescription_ShouldPersistDescription() {
        VacancyDTO dto = VacancyDTO.builder()
                .companyID(1)
                .link("https://acme.example/1")
                .title("Java Developer")
                .description("Build things.")
                .build();

        Vacancy vacancy = modelMapper.map(dto, Vacancy.class);

        assertThat(vacancy.getDescription()).isEqualTo("Build things.");
    }
}
