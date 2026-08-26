package com.tzevabest.reviews;

import com.tzevabest.support.IntegrationTest;
import com.tzevabest.support.TestDataFactory.TestProduct;
import com.tzevabest.support.TestDataFactory.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R-18: один отзыв на пару пользователь + объект. Уникальный индекс на уровне БД — то, что даёт 409
 * при гонке двух одновременных отправок (HTTP-эндпоинт отзывов — это ещё T-10.2).
 */
class ReviewUniquePerUserTest extends IntegrationTest {

    @Test
    void second_review_by_same_user_on_same_target_is_rejected() {
        TestUser user = factory.createUser();
        TestProduct product = factory.createProduct();
        insertReview(user.id(), product.id());

        // повтор тем же пользователем на тот же товар — отклоняется базой
        assertThatThrownBy(() -> insertReview(user.id(), product.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void same_user_may_review_different_targets() {
        TestUser user = factory.createUser();
        TestProduct a = factory.createProduct();
        TestProduct b = factory.createProduct();
        insertReview(user.id(), a.id());
        assertThatCode(() -> insertReview(user.id(), b.id())).doesNotThrowAnyException();
    }

    @Test
    void anonymous_reviews_are_not_constrained() {
        TestProduct product = factory.createProduct();
        insertReview(null, product.id());
        // отзывы без user_id (анонимные) не ограничиваются уникальностью
        assertThatCode(() -> insertReview(null, product.id())).doesNotThrowAnyException();
    }

    private void insertReview(Long userId, long targetId) {
        jdbcTemplate.update("""
                        insert into reviews (target_type, target_id, user_id, author_name, rating)
                        values ('PRODUCT', ?, ?, ?, ?)""",
                targetId, userId, "Reviewer", 5);
    }
}
