package com.tzevabest.common.dto;

import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Locale;

/**
 * Разобранный запрос сортировки. Каждый элемент {@code sort} вида {@code field}
 * или {@code field,asc|desc}; направление по умолчанию — ASC.
 *
 * @param orders список полей сортировки в порядке приоритета
 */
public record SortRequest(List<Order> orders) {

    public enum Direction { ASC, DESC }

    public record Order(String property, Direction direction) { }

    public static SortRequest empty() {
        return new SortRequest(List.of());
    }

    /** Разобрать «сырые» значения параметра {@code sort}. Пустые/битые элементы отбрасываются. */
    public static SortRequest parse(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return empty();
        }
        List<Order> parsed = raw.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(SortRequest::parseOne)
                .toList();
        return new SortRequest(parsed);
    }

    private static Order parseOne(String value) {
        String[] parts = value.split(",");
        String property = parts[0].trim();
        Direction direction = Direction.ASC;
        if (parts.length > 1) {
            String dir = parts[parts.length - 1].trim().toUpperCase(Locale.ROOT);
            if (dir.equals("DESC")) {
                direction = Direction.DESC;
            }
        }
        return new Order(property, direction);
    }

    /** Преобразовать в Spring {@link Sort}. */
    public Sort toSort() {
        if (orders == null || orders.isEmpty()) {
            return Sort.unsorted();
        }
        List<Sort.Order> springOrders = orders.stream()
                .map(o -> new Sort.Order(
                        o.direction() == Direction.DESC ? Sort.Direction.DESC : Sort.Direction.ASC,
                        o.property()))
                .toList();
        return Sort.by(springOrders);
    }
}
