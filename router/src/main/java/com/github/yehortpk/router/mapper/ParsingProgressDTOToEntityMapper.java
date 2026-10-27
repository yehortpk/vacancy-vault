package com.github.yehortpk.router.mapper;

import com.github.yehortpk.router.models.parser.ParsingProgress;
import com.github.yehortpk.router.models.parser.ParsingProgressDTO;
import org.modelmapper.PropertyMap;

public class ParsingProgressDTOToEntityMapper extends PropertyMap<ParsingProgressDTO, ParsingProgress> {
    @Override
    protected void configure() {
        map().setParsedVacancies(source.getParsedVacanciesTotalCount());
        map().setNewVacancies(source.getNewVacanciesTotalCount());
        map().setOutdatedVacancies(source.getOutdatedVacanciesTotalCount());
        map().setStartedAt(source.getStartedAt());
        map().setFinishedAt(source.getFinishedAt());
    }
}
