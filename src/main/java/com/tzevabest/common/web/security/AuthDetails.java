package com.tzevabest.common.web.security;

/**
 * Детали аутентификации: реальный IP клиента, разрешённый {@link ClientIpResolver} (учитывает
 * X-Forwarded-For от доверенных прокси). Заменяет стандартный WebAuthenticationDetails, чей
 * getRemoteAddress() за балансировщиком вернул бы адрес балансировщика. Идёт в audit_log/consents.
 */
public record AuthDetails(String ip) {
}
