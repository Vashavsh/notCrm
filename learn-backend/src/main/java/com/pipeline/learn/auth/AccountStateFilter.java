package com.pipeline.learn.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipeline.learn.account.Account;
import com.pipeline.learn.account.AccountRepository;
import com.pipeline.learn.account.AccountStatus;
import com.pipeline.learn.common.ApiExceptionHandler;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Runs on every request of a logged-in student and checks the database: the account must still exist,
 * be active, and carry the same session version as the session. That is what makes closing access or
 * resetting a password take effect on the very next request, instead of when a token happens to expire.
 */
public class AccountStateFilter extends OncePerRequestFilter {

    public static final String REVOKED_MESSAGE = "Доступ закрыт. Обратитесь к ментору.";
    public static final String REVOKED_CODE = "session_revoked";

    private static final Duration LAST_SEEN_THROTTLE = Duration.ofMinutes(5);

    private final AccountRepository accounts;
    private final ObjectMapper json;

    public AccountStateFilter(AccountRepository accounts, ObjectMapper json) {
        this.accounts = accounts;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LearnPrincipal principal) {
            Optional<Account> account = accounts.findById(principal.accountId());
            boolean valid = account.isPresent()
                    && account.get().getStatus() == AccountStatus.ACTIVE
                    && account.get().getSessionVersion() == principal.sessionVersion();
            if (!valid) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
                if (request.getRequestURI().startsWith("/api/")) {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setHeader("Cache-Control", "no-store");
                    response.getWriter().write(json.writeValueAsString(
                            ApiExceptionHandler.body(REVOKED_MESSAGE, REVOKED_CODE)));
                    return;
                }
            } else {
                Instant now = Instant.now();
                Instant lastSeen = account.get().getLastSeenAt();
                if (lastSeen == null || lastSeen.isBefore(now.minus(LAST_SEEN_THROTTLE))) {
                    accounts.touch(principal.accountId(), now, now.minus(LAST_SEEN_THROTTLE));
                }
            }
        }
        chain.doFilter(request, response);
    }
}
