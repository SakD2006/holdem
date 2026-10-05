package com.saksham.poker.server.web;

import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.exception.UnauthorizedException;
import com.saksham.poker.server.bootstrap.AppContext;
import com.saksham.poker.server.db.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lets an API request through only if it carries a valid login token, as
 * {@code Authorization: Bearer <token>}. Registering, logging in and the ping need no token.
 */
@WebFilter("/api/*")
public class AuthFilter extends HttpFilter {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    /** Request attribute holding the logged-in {@link User}. */
    static final String USER_ATTRIBUTE = "holdem.user";
    /** Request attribute holding the token the request came with. */
    static final String TOKEN_ATTRIBUTE = "holdem.token";

    private static final Set<String> OPEN_PATHS = Set.of("/api/ping", "/api/auth/register", "/api/auth/login");
    private static final String BEARER = "Bearer ";

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (OPEN_PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }
        String token = bearerToken(request.getHeader("Authorization"));
        User user;
        try {
            user = AppContext.from(getServletContext()).sessions().authenticate(token);
        } catch (UnauthorizedException e) {
            JsonSupport.writeError(response, e.code(), e.getMessage());
            return;
        } catch (RuntimeException e) {
            log.error("Could not check the login for {} {}", request.getMethod(), path, e);
            JsonSupport.writeError(response, ErrorCode.INTERNAL,
                    "Something went wrong on the server. Try again; if it keeps happening, check the server log.");
            return;
        }
        request.setAttribute(USER_ATTRIBUTE, user);
        request.setAttribute(TOKEN_ATTRIBUTE, token);
        chain.doFilter(request, response);
    }

    /** The token from an {@code Authorization: Bearer ...} header, or null if there is none. */
    static String bearerToken(String header) {
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        return header.substring(BEARER.length()).trim();
    }
}
