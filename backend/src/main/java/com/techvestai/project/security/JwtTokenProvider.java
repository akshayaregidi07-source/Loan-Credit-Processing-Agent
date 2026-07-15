package com.techvestai.project.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Handles JWT token generation, validation, and claim extraction.
 *
 * <p>Uses HMAC-SHA (HS256 / HS512 depending on key length) via jjwt 0.11.5.
 * The signing key is derived from the {@code jwtSecret} bean produced by
 * {@link com.techvestai.project.config.JwtConfig}, which enforces a minimum
 * length of 32 characters at startup.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(
            String jwtSecret,
            @Value("${spring.security.jwt.expiration:3600000}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Generates a signed JWT for the supplied {@link UserDetails}.
     *
     * @param userDetails the authenticated principal
     * @return compact, URL-safe JWT string
     */
    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Extracts the username (subject claim) from a token.
     *
     * @param token compact JWT string
     * @return the username embedded in the subject claim
     * @throws io.jsonwebtoken.JwtException if the token cannot be parsed
     */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Returns {@code true} if the token has a valid signature and is not expired.
     *
     * @param token compact JWT string
     * @return {@code true} if valid; {@code false} otherwise
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    // --- private helpers ---

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
