package com.tzevabest.catalog;

import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ограничение глубины дерева категорий на уровне БД (триггер enforce_category_max_depth).
 * R-14: перенос поддерева не должен проваливать потомков за 3-й уровень.
 * R-15: нарушение — DataIntegrityViolation (SQLSTATE 23514) → 409, а не 500.
 * HTTP-эндпоинта категорий ещё нет (T-4.1), поэтому проверяем на уровне JDBC: перевод исключения
 * Spring'ом в {@link DataIntegrityViolationException} — это то, что GlobalExceptionHandler отдаёт как 409.
 */
class CategoryDepthTest extends IntegrationTest {

    @Test
    void moving_subtree_past_three_levels_is_rejected() {
        // C(1) → D(2) → E(3)
        long c = insert(null, "c-" + unique());
        long d = insert(c, "d-" + unique());
        insert(d, "e-" + unique());
        // R1(1) → M(2)
        long r1 = insert(null, "r1-" + unique());
        long m = insert(r1, "m-" + unique());

        // Перенос C под M: C→3, D→4, E→5 — должно быть отклонено (высота поддерафа учитывается)
        assertThatThrownBy(() -> jdbcTemplate.update("update categories set parent_id = ? where id = ?", m, c))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void inserting_fourth_level_category_is_rejected_as_integrity_violation() {
        long l1 = insert(null, "l1-" + unique());
        long l2 = insert(l1, "l2-" + unique());
        long l3 = insert(l2, "l3-" + unique());

        // 4-й уровень — нарушение целостности (23514), которое маппится в 409, а не в 500
        assertThatThrownBy(() -> insert(l3, "l4-" + unique()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void three_levels_are_allowed() {
        long l1 = insert(null, "ok1-" + unique());
        long l2 = insert(l1, "ok2-" + unique());
        assertThatCode(() -> insert(l2, "ok3-" + unique())).doesNotThrowAnyException();

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from categories where slug like 'ok%'", Integer.class);
        assertThat(count).isEqualTo(3);
    }

    private long insert(Long parentId, String slug) {
        return jdbcTemplate.queryForObject(
                "insert into categories (parent_id, slug) values (?, ?) returning id",
                Long.class, parentId, slug);
    }

    private static String unique() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
