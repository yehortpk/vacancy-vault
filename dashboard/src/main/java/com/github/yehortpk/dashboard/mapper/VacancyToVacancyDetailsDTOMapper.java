package com.github.yehortpk.dashboard.mapper;

import com.github.yehortpk.dashboard.models.vacancy.Vacancy;
import com.github.yehortpk.dashboard.models.vacancy.VacancyDetailsDTO;
import org.modelmapper.PropertyMap;

public class VacancyToVacancyDetailsDTOMapper extends PropertyMap<Vacancy, VacancyDetailsDTO> {
    @Override
    protected void configure() {
        map().setId(source.getVacancyID());
        map().setURL(source.getLink());
        map().getCompany().setCompanyId(source.getCompanyId());
        map().getCompany().setCompanyTitle(source.getCompanyTitle());
        map().getCompany().setCompanyURL(source.getCompanyUrl());
    }
}
