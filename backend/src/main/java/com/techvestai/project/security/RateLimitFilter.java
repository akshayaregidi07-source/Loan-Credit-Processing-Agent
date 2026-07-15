package com.techvestai.project.security;

import com.techvestai.project.exception.TooManyRequestsException;
import com.techvestai.project.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that enforces per-user rate limiting after JWT authentication.
 *
 * <p>Applied after {@link JwtAuthenticationFilter} so only authenticated requests
 * are counted. Unauthenticated requests are rejected at 401 before reaching here.
 * On breach, returns HTTP 429 with a {@code Retry-After} header.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Only rate-limit authenticated requests
        if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String)) {
            String userId = auth.getName();
            if (!rateLimitService.isAllowed(userId)) {
                long retryAfter = rateLimitService.retryAfterSeconds(userId);
                response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE); // 429
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"status\":429,\"detail\":\"Too many requests. Please wait before trying again.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
