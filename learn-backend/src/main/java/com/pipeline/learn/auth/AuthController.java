package com.pipeline.learn.auth;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pipeline.learn.account.Account;
import com.pipeline.learn.account.AccountRepository;
import com.pipeline.learn.account.AccountStatus;
import com.pipeline.learn.common.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Student login, logout, own password change and the "who am I" call the front end starts with.
 * The response shape follows the old course site so its front end can be reused.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    /** Hash compared against when the login does not exist, so unknown logins cost the same time as wrong passwords. */
    private static final String DUMMY_HASH = new BCryptPasswordEncoder().encode("not-a-real-password");

    private static final int CHANGE_PASSWORD_MAX_FAILURES = 8;

    public record LoginRequest(String login, String password) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    /** {@code status}, {@code statusLabel}, {@code stage}, {@code email} and {@code isStaff} are fixed values kept only so the old front end keeps working. */
    public record UserView(String id, String name, String login, String email, boolean isStaff, String status,
            String statusLabel, int stage) {
    }

    public record SessionResponse(UserView user, String csrfToken) {
    }

    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final LoginRateLimiter limiter;
    private final PasswordPolicy policy;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AccountRepository accounts, PasswordEncoder encoder, LoginRateLimiter limiter,
            PasswordPolicy policy) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.limiter = limiter;
        this.policy = policy;
    }

    @GetMapping("/session")
    public SessionResponse session(CsrfToken csrf, Authentication authentication) {
        Account account = currentAccount(authentication);
        return new SessionResponse(account == null ? null : view(account), csrf.getToken());
    }

    @PostMapping("/login")
    public SessionResponse login(@RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response, CsrfToken csrf) {
        String login = body.login() == null ? "" : body.login().trim().toLowerCase(Locale.ROOT);
        if (login.length() > 150) {
            login = login.substring(0, 150);
        }
        String address = request.getRemoteAddr();
        limiter.checkAllowed(address, login);

        String password = body.password();
        boolean acceptable = PasswordPolicy.isAcceptableInput(password) && !login.isEmpty();
        Account account = acceptable ? accounts.findByLoginIgnoreCase(login).orElse(null) : null;
        boolean matches = acceptable && encoder.matches(password, account == null ? DUMMY_HASH : account.getPasswordHash());
        if (account == null || !matches) {
            limiter.recordFailure(address, login);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль.");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Доступ закрыт. Обратитесь к ментору.", "access_closed");
        }
        limiter.recordSuccess(address, login);

        // Never reuse a session that existed before login.
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        startSession(account, request, response);
        accounts.touch(account.getId(), java.time.Instant.now(), java.time.Instant.now());
        return new SessionResponse(view(account), csrf.getToken());
    }

    @PostMapping("/logout")
    public SessionResponse logout(HttpServletRequest request, CsrfToken csrf) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return new SessionResponse(null, csrf.getToken());
    }

    @PostMapping("/change-password")
    public SessionResponse changePassword(@RequestBody ChangePasswordRequest body, Authentication authentication,
            HttpServletRequest request, HttpServletResponse response, CsrfToken csrf) {
        Account account = currentAccount(authentication);
        if (account == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Войдите в аккаунт.");
        }
        String key = "change:" + account.getId();
        limiter.checkAllowedKey(key);
        if (!PasswordPolicy.isAcceptableInput(body.currentPassword())
                || !encoder.matches(body.currentPassword(), account.getPasswordHash())) {
            limiter.recordFailureKey(key, CHANGE_PASSWORD_MAX_FAILURES);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Текущий пароль введён неверно.");
        }
        policy.validate(body.newPassword(), account.getLogin());

        account.changePassword(encoder.encode(body.newPassword()));
        accounts.save(account);
        // Every other session of this account now carries an old version and is rejected; this one stays.
        startSession(account, request, response);
        return new SessionResponse(view(account), csrf.getToken());
    }

    private void startSession(Account account, HttpServletRequest request, HttpServletResponse response) {
        LearnPrincipal principal = new LearnPrincipal(account.getId(), account.getSessionVersion());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    private Account currentAccount(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LearnPrincipal principal) {
            return accounts.findById(principal.accountId()).orElse(null);
        }
        return null;
    }

    private static UserView view(Account account) {
        return new UserView(account.getId().toString(), account.getFullName(), account.getLogin(), null, false,
                "studying", "Учится", 1);
    }
}
