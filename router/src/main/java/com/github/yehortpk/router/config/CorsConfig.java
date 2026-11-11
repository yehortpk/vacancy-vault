package com.github.yehortpk.router.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS rules for the Angular frontend.
 *
 * <p>Exposed as a {@link CorsConfigurationSource} bean rather than through a
 * {@code WebMvcConfigurer}, because the latter does not apply to Spring Security's filter
 * chain — {@code WebSecurityConfig} consumes this bean via {@code http.cors(...)}.
 */
@Configuration
public class CorsConfig {
    @Value("${frontend-service-url}")
    private String frontendServiceURL;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // An Origin header carries no path or trailing slash, so "http://localhost:4200/"
        // never matched what the browser actually sends.
        configuration.setAllowedOrigins(List.of(frontendServiceURL, "http://localhost:4200"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
