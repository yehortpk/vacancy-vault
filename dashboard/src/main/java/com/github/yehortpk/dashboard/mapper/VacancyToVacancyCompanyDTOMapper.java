package com.github.yehortpk.dashboard.mapper;

import com.github.yehortpk.dashboard.models.vacancy.Vacancy;
import com.github.yehortpk.dashboard.models.vacancy.VacancyCompanyDTO;
import org.modelmapper.PropertyMap;

public class VacancyToVacancyCompanyDTOMapper extends PropertyMap<Vacancy, VacancyCompanyDTO> {
    @Override
    protected void configure() {
        map().setId(source.getVacancyID());
        map().setURL(source.getLink());
        map().getCompany().setCompanyId(source.getCompanyId());
        map().getCompany().setCompanyTitle(source.getCompanyTitle());
        map().getCompany().setCompanyURL(source.getCompanyUrl());
    }
}
