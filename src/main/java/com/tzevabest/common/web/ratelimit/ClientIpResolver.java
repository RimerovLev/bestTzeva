package com.tzevabest.common.web.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Определение реального IP клиента за балансировщиком. X-Forwarded-For учитывается только если
 * непосредственный отправитель (getRemoteAddr) — доверенный прокси; тогда берётся самый правый
 * НЕдоверенный адрес в цепочке. Иначе — getRemoteAddr (защита от подделки XFF).
 * Доверенные прокси задаются CIDR-списком {@code app.security.trusted-proxies}.
 */
@Component
public class ClientIpResolver {

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(@Value("${app.security.trusted-proxies:}") List<String> cidrs) {
        this.trustedProxies = cidrs.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .map(IpAddressMatcher::new)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank() || !isTrusted(remote)) {
            return remote;
        }
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String ip = hops[i].trim();
            if (!ip.isEmpty() && !isTrusted(ip)) {
                return ip;
            }
        }
        return hops[0].trim();
    }

    private boolean isTrusted(String ip) {
        return trustedProxies.stream().anyMatch(m -> safeMatch(m, ip));
    }

    private static boolean safeMatch(IpAddressMatcher matcher, String ip) {
        try {
            return matcher.matches(ip);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
