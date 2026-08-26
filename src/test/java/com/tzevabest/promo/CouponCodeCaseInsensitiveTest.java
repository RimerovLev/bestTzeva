package com.tzevabest.promo;

import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R-29: коды купонов регистронезависимы — уникальность по lower(code), поиск тоже по lower.
 */
class CouponCodeCaseInsensitiveTest extends IntegrationTest {

    @Test
    void duplicate_code_differing_only_in_case_is_rejected() {
        insertCoupon("SUMMER10");
        assertThatThrownBy(() -> insertCoupon("summer10"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void coupon_is_found_by_lowercased_input() {
        insertCoupon("SUMMER10");
        Integer found = jdbcTemplate.queryForObject(
                "select count(*) from coupons where lower(code) = lower(?)", Integer.class, "summer10");
        assertThat(found).isEqualTo(1);
    }

    private void insertCoupon(String code) {
        jdbcTemplate.update(
                "insert into coupons (code, type, value) values (?, 'PERCENT', 10)", code);
    }
}
