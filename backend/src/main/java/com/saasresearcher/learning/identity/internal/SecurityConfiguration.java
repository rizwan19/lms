package com.saasresearcher.learning.identity.internal;

import com.saasresearcher.learning.common.ErrorCodes;
import com.saasresearcher.learning.common.WebConfiguration;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration implements WebMvcConfigurer {

    private static final String API = WebConfiguration.API_BASE;

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, CsrfTokenRepository csrfTokens, SecurityContextRepository contexts,
            UserAccountRepository accounts, ProblemWriter problems) throws Exception {
        return http
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokens))
                .securityContext(context -> context.securityContextRepository(contexts))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .logout(logout -> logout
                        .logoutUrl(API + "/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, API + "/auth/csrf", API + "/setup", API + "/institution").permitAll()
                        .requestMatchers(HttpMethod.POST, API + "/auth/login", API + "/setup", API + "/auth/invitations/accept",
                                API + "/auth/password-reset/request", API + "/auth/password-reset/confirm").permitAll()
                        .requestMatchers(API + "/public/**", "/error", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) ->
                                problems.write(response, HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED, "Sign in required"))
                        .accessDeniedHandler((request, response, ex) -> {
                            String code = ex instanceof CsrfException ? IdentityErrors.CSRF_REJECTED : ErrorCodes.ACCESS_DENIED;
                            problems.write(response, HttpStatus.FORBIDDEN, code, "Request was not permitted");
                        }))
                .addFilterAfter(new ActiveAccountFilter(accounts, problems), AnonymousAuthenticationFilter.class)
                .build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Rotates the session ID and the CSRF token whenever authentication changes. */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokens)));
    }

    @Bean
    PasswordEncoder passwordEncoder(IdentityProperties properties) {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(properties.passwordHashStrength())));
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new ActorArgumentResolver());
    }
}
