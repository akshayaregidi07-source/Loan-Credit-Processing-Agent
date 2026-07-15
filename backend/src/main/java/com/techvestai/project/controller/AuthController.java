package com.techvestai.project.controller;

import com.techvestai.project.dto.request.LoginRequest;
import com.techvestai.project.dto.response.AuthResponse;
import com.techvestai.project.security.JwtTokenProvider;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication controller — Task 13.1.
 *
 * <p>POST /api/v1/auth/login authenticates the user and returns a signed JWT.
 * On failure Spring Security raises an {@link org.springframework.security.core.AuthenticationException}
 * which is handled by {@link com.techvestai.project.exception.GlobalExceptionHandler} → 401.
 *
 * <p><b>Requirements:</b> 1.1
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider      jwtTokenProvider;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider      = jwtTokenProvider;
    }

    /**
     * Authenticates username/password and returns a signed JWT together with
     * the user's role so the frontend can redirect to the correct dashboard.
     *
     * @param request validated login credentials
     * @return 200 with {@link AuthResponse}; 401 on bad credentials
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        String token = jwtTokenProvider.generateToken(
                (org.springframework.security.core.userdetails.UserDetails) auth.getPrincipal());

        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("");

        return ResponseEntity.ok(new AuthResponse(token, role));
    }
}
