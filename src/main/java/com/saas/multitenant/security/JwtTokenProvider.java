package com.saas.multitenant.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final SecretKey tenantSigningKey;

    public JwtTokenProvider(@Value("${app.security.jwt-secret}") String jwtSecret) {
        this.tenantSigningKey = Keys.hmacShaKeyFor(secretBytes(jwtSecret));
    }

    public String extractTenantId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(tenantSigningKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("tenantId", String.class);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static byte[] secretBytes(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ignored) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
