package com.github.yehortpk.dashboard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS rules for the Angular frontend, which only reads here.
 *
 * <p>A plain {@link WebMvcConfigurer} is enough: the dashboard has no Spring Security filter
 * chain for the mappings to be invisible to, and no credentials to allow.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Value("${frontend-service-url}")
    private String frontendServiceURL;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // An Origin header carries no path or trailing slash
        registry.addMapping("/**")
                .allowedOrigins(frontendServiceURL, "http://localhost:4200")
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*");
    }
}
