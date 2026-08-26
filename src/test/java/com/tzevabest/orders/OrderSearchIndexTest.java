package com.tzevabest.orders;

import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-20: админский поиск заказов по телефону/email/имени идёт через индекс, а не Seq Scan.
 * Датасет соответствует профилю large (100k заказов) — на меньшем объёме планировщику всё равно
 * и сравнение ничего не доказывало бы (см. docs/db-query-plans.md).
 */
class OrderSearchIndexTest extends IntegrationTest {

    private static final int ORDERS = 100_000;

    @Test
    void phone_lookup_uses_index_on_large_dataset() {
        seedOrders();

        // g=50000 → '+972500050000'
        String plan = explain(
                "select id from orders where customer_phone = '+972500050000'");
        System.out.println("[R-20] phone plan:\n" + plan);

        assertThat(plan).doesNotContain("Seq Scan on orders");
        assertThat(plan.toLowerCase()).contains("ix_orders_customer_phone");
    }

    @Test
    void email_and_name_lookups_use_indexes_on_large_dataset() {
        seedOrders();

        String emailPlan = explain(
                "select id from orders where lower(customer_email) = 'buyer50000@x.test'");
        System.out.println("[R-20] email plan:\n" + emailPlan);
        assertThat(emailPlan).doesNotContain("Seq Scan on orders");
        assertThat(emailPlan.toLowerCase()).contains("ix_orders_customer_email_lower");

        String namePlan = explain(
                "select id from orders where customer_name ilike '%Client 50000%'");
        System.out.println("[R-20] name plan:\n" + namePlan);
        assertThat(namePlan).doesNotContain("Seq Scan on orders");
        assertThat(namePlan.toLowerCase()).contains("ix_orders_customer_name_trgm");
    }

    private void seedOrders() {
        jdbcTemplate.update("""
                insert into orders (order_number, customer_email, customer_phone, customer_name, delivery_method)
                select 'TB-IDX-' || g,
                       'buyer' || g || '@x.test',
                       '+97250' || lpad(g::text, 7, '0'),
                       'Client ' || g,
                       'PICKUP'
                from generate_series(1, ?) g
                """, ORDERS);
        jdbcTemplate.execute("analyze orders");
    }

    private String explain(String sql) {
        List<String> lines = jdbcTemplate.queryForList("explain (analyze, costs off) " + sql, String.class);
        return String.join("\n", lines);
    }
}
