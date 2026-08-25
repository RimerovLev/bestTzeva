package com.tzevabest.common.web;

import com.tzevabest.common.dto.SortRequest;
import com.tzevabest.common.web.error.BadRequestException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.Set;

/**
 * Резолвер параметров {@code page}, {@code size}, {@code sort} в {@link PageQuery}.
 * <ul>
 *   <li>{@code page} — с нуля, отрицательное нормализуется в 0;</li>
 *   <li>{@code size} — дефолт {@value #DEFAULT_SIZE}, максимум {@value #MAX_SIZE};
 *       превышение максимума <b>обрезается</b> до {@value #MAX_SIZE}, а не ошибка;</li>
 *   <li>{@code sort} — повторяемый параметр вида {@code field} или {@code field,desc}.</li>
 * </ul>
 */
public class ApiPageArgumentResolver implements HandlerMethodArgumentResolver {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return PageQuery.class.equals(parameter.getParameterType())
                || parameter.hasParameterAnnotation(ApiPage.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        int page = parseInt(webRequest.getParameter("page"), 0);
        if (page < 0) {
            page = 0;
        }

        int size = parseInt(webRequest.getParameter("size"), DEFAULT_SIZE);
        if (size < 1) {
            size = DEFAULT_SIZE;
        } else if (size > MAX_SIZE) {
            size = MAX_SIZE;
        }

        String[] sortValues = webRequest.getParameterValues("sort");
        SortRequest sort = SortRequest.parse(sortValues != null ? List.of(sortValues) : List.of());

        validateSortFields(sort, parameter.getParameterAnnotation(ApiPage.class));

        return new PageQuery(page, size, sort);
    }

    /** Поле сортировки вне allow-list эндпоинта — 400, чтобы не сортировать по произвольным полям. */
    private static void validateSortFields(SortRequest sort, ApiPage annotation) {
        if (sort.orders().isEmpty()) {
            return;
        }
        Set<String> allowed = (annotation != null) ? Set.of(annotation.sort()) : Set.of();
        for (SortRequest.Order order : sort.orders()) {
            if (!allowed.contains(order.property())) {
                throw new BadRequestException("common.request.sort_invalid", order.property());
            }
        }
    }

    private static int parseInt(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
