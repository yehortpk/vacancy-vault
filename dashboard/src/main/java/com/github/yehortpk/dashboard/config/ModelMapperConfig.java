package com.github.yehortpk.dashboard.config;

import com.github.yehortpk.dashboard.mapper.DataPageToVacanciesPageDTOMapper;
import com.github.yehortpk.dashboard.mapper.VacancyToCompanyShortInfoDTOMapper;
import com.github.yehortpk.dashboard.mapper.VacancyToVacancyCompanyDTOMapper;
import com.github.yehortpk.dashboard.mapper.VacancyToVacancyDetailsDTOMapper;
import com.github.yehortpk.dashboard.utils.ServerClock;
import org.modelmapper.AbstractConverter;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Configuration
public class ModelMapperConfig {
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();

        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setSkipNullEnabled(true)
                .setFieldMatchingEnabled(false)
                .setFieldAccessLevel(org.modelmapper.config.Configuration.AccessLevel.PRIVATE);

        // Applies wherever a mapping meets the pair, including inside the vacancy page lists
        modelMapper.addConverter(new AbstractConverter<LocalDateTime, OffsetDateTime>() {
            @Override
            protected OffsetDateTime convert(LocalDateTime source) {
                return ServerClock.toOffsetDateTime(source);
            }
        });

        modelMapper.addMappings(new DataPageToVacanciesPageDTOMapper());
        modelMapper.addMappings(new VacancyToVacancyCompanyDTOMapper());
        modelMapper.addMappings(new VacancyToVacancyDetailsDTOMapper());
        modelMapper.addMappings(new VacancyToCompanyShortInfoDTOMapper());

        return modelMapper;
    }
}
