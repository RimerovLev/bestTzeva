package com.tzevabest.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Сквозной идентификатор запроса.
 * <p>
 * Если во входящем заголовке {@value #HEADER} пришёл идентификатор — он используется как
 * {@code trace_id}; иначе генерируется новый. Значение кладётся в MDC (ключ {@value #MDC_KEY},
 * тот же, что использует {@code GlobalExceptionHandler}) и возвращается в ответе в заголовке
 * {@value #HEADER}.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "trace_id";

    /**
     * Допустимый формат входящего идентификатора. Значение уходит в MDC и в логи,
     * поэтому запрещаем переводы строк (подделка строк лога) и длинный мусор.
     */
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || !SAFE.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
