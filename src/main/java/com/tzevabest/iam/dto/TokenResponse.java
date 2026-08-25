package com.tzevabest.iam.dto;

/** access + refresh, token_type=Bearer, expires_in — время жизни access в секундах. */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}
