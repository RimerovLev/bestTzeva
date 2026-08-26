package com.tzevabest.iam.service;

import com.tzevabest.iam.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Выпуск и разбор JWT. Access — короткоживущий ({@code type=access}, claims sub=userId, role, email,
 * locale), refresh — с {@code jti} (tokenId) и {@code type=refresh} для ротации в
 * {@link RefreshTokenStore}. Оба токена подписаны одним ключом и имеют одного издателя, поэтому claim
 * {@code type} — единственное, что не даёт предъявить refresh как access (и наоборот): {@code parseAccess}
 * требует {@code type=access}, {@code parseRefresh} — {@code type=refresh}.
 */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.issuer();
        this.accessTtl = properties.accessTtl();
        this.refreshTtl = properties.refreshTtl();
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    public String generateAccess(long userId, String role, String email, String locale) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(Long.toString(userId))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim("role", role)
                .claim("email", email)
                .claim("locale", locale)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    public GeneratedRefresh generateRefresh(long userId) {
        Instant now = Instant.now();
        String tokenId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(Long.toString(userId))
                .id(tokenId)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTtl)))
                .signWith(key)
                .compact();
        return new GeneratedRefresh(tokenId, token);
    }

    public AccessClaims parseAccess(String token) {
        Claims claims = parse(token);
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Not an access token");
        }
        String role = claims.get("role", String.class);
        if (role == null || role.isBlank()) {
            // без роли authority была бы ROLE_null — не пускаем такой токен вовсе
            throw new JwtException("Access token has no role");
        }
        return new AccessClaims(
                Long.parseLong(claims.getSubject()),
                role,
                claims.get("email", String.class),
                claims.get("locale", String.class));
    }

    public RefreshClaims parseRefresh(String token) {
        Claims claims = parse(token);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Not a refresh token");
        }
        return new RefreshClaims(Long.parseLong(claims.getSubject()), claims.getId());
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .requireIssuer(issuer)
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public record GeneratedRefresh(String tokenId, String token) {
    }

    public record AccessClaims(long userId, String role, String email, String locale) {
    }

    public record RefreshClaims(long userId, String tokenId) {
    }
}
