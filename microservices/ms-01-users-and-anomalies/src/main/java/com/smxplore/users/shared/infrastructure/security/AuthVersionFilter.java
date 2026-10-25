package com.smxplore.users.shared.infrastructure.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.smxplore.users.identity.application.port.out.UserRepository;
import com.smxplore.users.identity.domain.model.UserId;
import com.smxplore.users.identity.domain.model.Usuario;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthVersionFilter extends OncePerRequestFilter {

    private final UserRepository users;
    private final Cache<UUID, Long> cache;

    public AuthVersionFilter(UserRepository users, Cache<UUID, Long> cache) {
        this.users = users;
        this.cache = cache;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            Jwt jwt = jwtAuthentication.getToken();
            Object versionClaim = jwt.getClaim("ver");
            if (!(versionClaim instanceof Number version)) {
                unauthorized(response);
                return;
            }

            UUID userId;
            try {
                userId = UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException | NullPointerException exception) {
                unauthorized(response);
                return;
            }

            Long currentVersion = cache.get(
                    userId,
                    id -> users.findById(new UserId(id))
                            .map(Usuario::authVersion)
                            .orElse(-1L));

            if (currentVersion == null || currentVersion != version.longValue()) {
                unauthorized(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static void unauthorized(HttpServletResponse response) throws IOException {
        SecurityProblemWriter.write(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized",
                "La sesión ya no es válida.",
                "UNAUTHORIZED");
    }
}
