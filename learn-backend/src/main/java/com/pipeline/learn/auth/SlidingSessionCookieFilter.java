package com.pipeline.learn.auth;

import java.io.IOException;

import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Spring Session writes the session cookie once, at login, so its lifetime would end 90 days after
 * login no matter how often the student comes back. This filter rewrites the cookie on every request
 * of a live session, which makes the 90 days sliding, as on the old site.
 */
public class SlidingSessionCookieFilter extends OncePerRequestFilter {

    private final CookieSerializer cookies;

    public SlidingSessionCookieFilter(CookieSerializer cookies) {
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            cookies.writeCookieValue(new CookieValue(request, response, session.getId()));
        }
        chain.doFilter(request, response);
    }
}
