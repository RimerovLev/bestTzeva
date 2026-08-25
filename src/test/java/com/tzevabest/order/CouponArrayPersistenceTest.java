package com.tzevabest.order;

import com.tzevabest.order.domain.Coupon;
import com.tzevabest.order.domain.CouponAppliesTo;
import com.tzevabest.order.domain.CouponType;
import com.tzevabest.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Round-trip массива applies_to_ids (bigint[]) в купоне. */
class CouponArrayPersistenceTest extends IntegrationTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private PlatformTransactionManager txManager;

    private TransactionTemplate tx;

    @BeforeEach
    void initTx() {
        tx = new TransactionTemplate(txManager);
    }

    @Test
    void coupon_applies_to_ids_array_round_trips() {
        Long id = tx.execute(status -> {
            Coupon coupon = new Coupon();
            coupon.setCode("SAVE-" + UUID.randomUUID().toString().substring(0, 8));
            coupon.setType(CouponType.PERCENT);
            coupon.setValue(new BigDecimal("10.00"));
            coupon.setAppliesTo(CouponAppliesTo.CATEGORY);
            coupon.setAppliesToIds(List.of(11L, 22L, 33L));
            em.persist(coupon);
            em.flush();
            return coupon.getId();
        });

        tx.executeWithoutResult(status -> {
            em.clear();
            Coupon loaded = em.find(Coupon.class, id);
            assertThat(loaded.getAppliesToIds()).containsExactly(11L, 22L, 33L);
        });
    }
}
