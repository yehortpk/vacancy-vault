package com.github.yehortpk.router.mapper;

import com.github.yehortpk.router.models.vacancy.Vacancy;
import com.github.yehortpk.router.models.vacancy.VacancyDetailsDTO;
import org.modelmapper.PropertyMap;

public class VacancyToVacancyDetailsDTOMapper extends PropertyMap<Vacancy, VacancyDetailsDTO> {
    @Override
    protected void configure() {
        map().setId(source.getVacancyID());
        map().setURL(source.getLink());
    }
}
