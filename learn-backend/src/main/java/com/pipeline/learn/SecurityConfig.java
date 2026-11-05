package com.pipeline.learn;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.session.web.http.CookieSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipeline.learn.account.AccountRepository;
import com.pipeline.learn.auth.AccountStateFilter;
import com.pipeline.learn.auth.SlidingSessionCookieFilter;
import com.pipeline.learn.common.ApiExceptionHandler;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Login is a server-side session kept in the database (so restarts do not log students out and a
 * revoked account is cut off on its next request). Writes need the CSRF token the front end gets from
 * {@code GET /api/session} and sends in the {@code X-CSRFToken} header.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccountRepository accounts, ObjectMapper json,
            CookieSerializer sessionCookies, @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies)
            throws Exception {

        CookieCsrfTokenRepository csrf = new CookieCsrfTokenRepository();
        csrf.setCookieName("LEARN_CSRF");
        csrf.setHeaderName("X-CSRFToken");
        csrf.setCookieCustomizer(cookie -> cookie.httpOnly(true).sameSite("Lax").secure(secureCookies).path("/"));

        http
                // The CSRF token reaches the page in the JSON of /api/session, so the cookie itself can stay HttpOnly.
                .csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/session", "/api/login", "/api/logout").permitAll()
                        .anyRequest().authenticated())
                // Without this every unauthenticated hit would store a "saved request" and create a session row.
                .requestCache(c -> c.requestCache(new NullRequestCache()))
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(json, response, HttpStatus.UNAUTHORIZED, "Войдите в аккаунт.", null))
                        .accessDeniedHandler((request, response, e) ->
                                writeError(json, response, HttpStatus.FORBIDDEN,
                                        "Сессия обновилась. Обновите страницу и повторите действие.", "csrf")))
                .addFilterAfter(new AccountStateFilter(accounts, json), SecurityContextHolderFilter.class)
                .addFilterAfter(new SlidingSessionCookieFilter(sessionCookies), AccountStateFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeError(ObjectMapper json, HttpServletResponse response, HttpStatus status, String message,
            String code) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(json.writeValueAsString(ApiExceptionHandler.body(message, code)));
    }
}
