package com.github.yehortpk.dashboard.mapper;

import com.github.yehortpk.dashboard.models.company.CompanyShortInfoDTO;
import com.github.yehortpk.dashboard.models.vacancy.Vacancy;
import org.modelmapper.PropertyMap;

public class VacancyToCompanyShortInfoDTOMapper extends PropertyMap<Vacancy, CompanyShortInfoDTO> {
    @Override
    protected void configure() {
        map().setCompanyId(source.getCompanyId());
        map().setCompanyTitle(source.getCompanyTitle());
        map().setCompanyURL(source.getCompanyUrl());
    }
}
