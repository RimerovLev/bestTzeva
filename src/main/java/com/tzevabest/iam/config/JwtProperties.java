package com.tzevabest.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Настройки JWT. secret — не короче 32 байт (HS256). */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTtl, Duration refreshTtl) {
}
