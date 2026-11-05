package com.pipeline.learn.auth;

import java.io.Serializable;
import java.util.UUID;

import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Who is logged in, as stored in the server-side session. {@code sessionVersion} is the account's
 * version at login time; when the account's version moves on (password reset, closed access) the
 * session is rejected on its next request.
 */
public record LearnPrincipal(UUID accountId, int sessionVersion) implements AuthenticatedPrincipal, Serializable {

    @Override
    public String getName() {
        return accountId.toString();
    }
}
