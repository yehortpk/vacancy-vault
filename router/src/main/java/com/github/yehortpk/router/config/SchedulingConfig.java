package com.github.yehortpk.router.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Switches on {@code @Scheduled}, which {@code NotifiedVacancySweeper} runs on. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
