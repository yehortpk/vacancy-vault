package com.github.yehortpk.dashboard.models.vacancy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VacanciesPageDTO {
    private List<VacancyCompanyDTO> vacancies;
    private int currentPage;
    private int pageSize;
    private int totalVacancies;
    private int totalPages;
}
