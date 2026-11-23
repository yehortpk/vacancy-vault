package com.github.yehortpk.router.config;

import com.github.yehortpk.router.models.user.UserRole;
import com.github.yehortpk.router.security.JwtAuthFilter;
import com.github.yehortpk.router.security.JwtService;
import com.github.yehortpk.router.security.ServiceTokenAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Stateless security for the router.
 *
 * <p>The router serves two callers: a signed-in user, whose bearer token opens that
 * account's own room under {@code /me}, and a provider's service, whose shared secret opens
 * {@code /connector}. The token is signed and verified here alone, with a secret no other
 * service holds.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {
    private final JwtService jwtService;

    @Value("${service.connector-token}")
    private String serviceConnectorToken;

    /**
     * Both authentication filters are built here rather than annotated {@code @Component},
     * because a filter bean is also picked up by Boot's servlet filter registration and
     * would then run outside the security chain as well as inside it. Outside, it populates
     * the context before the chain's own filters get to it, and the chain then clears that
     * context — which shows up as an intermittent 401 on requests that were authenticated
     * perfectly well.
     */
    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtService);
    }

    @Bean
    public ServiceTokenAuthFilter serviceTokenAuthFilter() {
        return new ServiceTokenAuthFilter(serviceConnectorToken);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Answers an unauthenticated request with a bare 401.
     *
     * <p>The default entry point sends a {@code WWW-Authenticate: Basic} challenge, which
     * makes a browser pop up its own credentials dialog instead of letting the Angular
     * interceptor handle the refresh.
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return new HttpStatusEntryPoint(UNAUTHORIZED);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // A WebMvcConfigurer's CORS mappings do not apply to Spring Security's
                // filter chain, so CORS is wired in explicitly from CorsConfig's bean.
                .cors(Customizer.withDefaults())
                // No browser session or form to protect: the credential is a header, not a cookie
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        // Preflight must never require credentials
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // The compose healthcheck gates the dependent services' startup on this
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        // Called by provider services with a shared secret, never by a browser
                        .requestMatchers("/connector/**").hasRole("SERVICE")
                        // Each account's own room, its filters included; an admin has one too
                        .requestMatchers("/me/**").hasAnyRole(UserRole.USER.name(), UserRole.ADMIN.name())
                        .anyRequest().permitAll())
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(authenticationEntryPoint()))
                .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(serviceTokenAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }
}
