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


// Parses and validates JWT Bearer tokens
// Extracts the tenantId claim for use by TenantIdentificationFilter

@Slf4j
@Component
public class JwtTokenProvider 
{

    private final SecretKey signingKey;

    public JwtTokenProvider(@Value("${app.security.jwt-secret}") String secret) 
    {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Extracts the tenantId claim from a Bearer token.
     /**
     * @param token raw JWT string (without "Bearer " prefix)
     * @return tenantId claim value, or null if token is invalid/expired
     */
    
    public String extractTenantId(String token) 
    {
        try 
        {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("tenantId", String.class);
        } 
        catch (JwtException | IllegalArgumentException e) 
        {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String token) 
    {
        return extractTenantId(token) != null;
    }
}