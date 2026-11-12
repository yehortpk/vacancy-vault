package com.github.yehortpk.router.config;

import com.github.yehortpk.router.mapper.*;
import com.github.yehortpk.router.utils.ServerClock;
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

        modelMapper.addMappings(new CompanyToCompanyShortDTOMapper());
        modelMapper.addMappings(new VacancyToVacancyDTOMapper());
        modelMapper.addMappings(new VacancyDTOToVacancyMapper());
        modelMapper.addMappings(new VacancyToVacancyShortDTOMapper());
        modelMapper.addMappings(new DataPageToVacanciesPageDTOMapper());
        modelMapper.addMappings(new VacancyToVacancyCompanyDTOMapper());
        modelMapper.addMappings(new ParsingProgressDTOToEntityMapper());

        return modelMapper;
    }
}
