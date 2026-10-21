package com.github.yehortpk.router.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless security for the router.
 *
 * <p>Adding spring-boot-starter-security switches on HTTP Basic over every endpoint by
 * default, which would 401 the parser's ingest calls and the dashboard's anonymous reads.
 * This chain keeps today's access rules intact; authentication arrives with the
 * {@code /auth} and {@code /me} endpoints.
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // A WebMvcConfigurer's CORS mappings do not apply to Spring Security's
                // filter chain, so CORS is wired in explicitly from CorsConfig's bean.
                .cors(Customizer.withDefaults())
                // No browser session or form to protect, and the parser posts without a token
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        // Preflight must never require credentials
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // The compose healthcheck gates the bot's startup on this
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // The parser calls these with no credentials and the dashboard reads
                        // vacancies anonymously. Locking the ingest pipeline down needs a
                        // service token on the parser side too, and is deliberately deferred.
                        .requestMatchers("/vacancy/**", "/company/**", "/parser/**").permitAll()
                        .requestMatchers("/filter/**").permitAll()
                        .anyRequest().permitAll())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }
}
