package com.github.yehortpk.crawler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.yehortpk.crawler.security.AdminDetailsService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Session security for the crawler's own admin panel.
 *
 * <p>The panel is served from the same origin as this API (its dev server proxies the calls),
 * so an admin is a plain session cookie: no token to hand out, and no CORS. The router's users
 * and its JWTs mean nothing here.
 *
 * <p>A cookie is sent by the browser on its own, so state-changing requests are CSRF-checked
 * the way Angular's {@code HttpClient} supports out of the box: the token is readable in the
 * {@code XSRF-TOKEN} cookie and comes back in the {@code X-XSRF-TOKEN} header.
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * What {@code POST /login} checks credentials with: the {@code admin} table, through
     * {@link AdminDetailsService}, against the BCrypt hash stored there.
     */
    @Bean
    public AuthenticationManager authenticationManager(AdminDetailsService adminDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(adminDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /** Where the login endpoint stores the authenticated admin, and the chain reads it back. */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * The plain handler rather than the default one, which expects the token masked per
     * request: Angular echoes the cookie's raw value. With no attribute name the token is
     * loaded on every request, so the cookie is already there when the first one comes back,
     * a 401 included, and the login that follows can carry it.
     */
    private CsrfTokenRequestAttributeHandler csrfTokenRequestHandler() {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);
        return requestHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository securityContextRepository)
            throws Exception {
        http
                .csrf(csrf -> csrf
                        // Readable by script, which is the point: the panel copies it into a header
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfTokenRequestHandler()))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/login", "/error").permitAll()
                        // Starting a parse and watching it; everything else is closed as well
                        .anyRequest().authenticated())
                // A bare 401, which the panel turns into its login page. The default entry point
                // would redirect to a login form this service does not have.
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }
}
