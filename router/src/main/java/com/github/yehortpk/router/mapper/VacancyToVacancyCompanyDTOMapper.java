package com.github.yehortpk.router.mapper;

import com.github.yehortpk.router.models.vacancy.Vacancy;
import com.github.yehortpk.router.models.vacancy.VacancyCompanyDTO;
import org.modelmapper.PropertyMap;

public class VacancyToVacancyCompanyDTOMapper extends PropertyMap<Vacancy, VacancyCompanyDTO> {
    @Override
    protected void configure() {
        map().setId(source.getVacancyID());
        map().setURL(source.getLink());
    }
}