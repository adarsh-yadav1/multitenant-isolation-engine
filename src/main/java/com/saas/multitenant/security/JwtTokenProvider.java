package com.saas.multitenant.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey tenantSigningKey;
    private final SecretKey adminSigningKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${app.security.jwt-secret}") String tenantSecret,
            @Value("${app.security.admin-jwt-secret}") String adminSecret,
            @Value("${app.security.jwt-expiration-ms:86400000}") long expirationMs) {
        this.tenantSigningKey = Keys.hmacShaKeyFor(
                tenantSecret.getBytes(StandardCharsets.UTF_8));
        this.adminSigningKey = Keys.hmacShaKeyFor(
                adminSecret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateTenantToken(String tenantId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(tenantId)
                .claim("tenantId", tenantId)
                .claim("type", "TENANT")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(tenantSigningKey)
                .compact();
    }

    public String extractTenantId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(tenantSigningKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("tenantId", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid tenant JWT: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String token) {
        return extractTenantId(token) != null;
    }

    public String generateAdminToken(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("role", "ADMIN")
                .claim("type", "ADMIN")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(adminSigningKey)
                .compact();
    }

    public boolean validateAdminToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(adminSigningKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return "ADMIN".equals(claims.get("role", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid admin JWT: {}", e.getMessage());
            return false;
        }
    }
}