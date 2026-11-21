package com.github.yehortpk.parser.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Switches on {@code @Scheduled}, which {@code OutdatedVacancySweeper} runs on. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
