package com.tzevabest.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Фабрика тестовых данных: создаёт пользователя, товар и заказ с разумными дефолтами
 * и возможностью переопределить отдельные поля через customizer.
 * <p>
 * Все таблицы (users V2, каталог V3, склад V4, заказы V5) созданы Flyway — фабрика
 * пишет в реальные таблицы.
 */
public class TestDataFactory {

    private final JdbcTemplate jdbc;

    public TestDataFactory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // ── User ────────────────────────────────────────────────────────────────────

    public TestUser createUser() {
        return createUser(spec -> { });
    }

    public TestUser createUser(Consumer<UserSpec> customizer) {
        UserSpec spec = new UserSpec();
        customizer.accept(spec);
        Long id = jdbc.queryForObject("""
                        INSERT INTO users (email, phone, password_hash, first_name, last_name, role, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        RETURNING id""",
                Long.class,
                spec.email, spec.phone, spec.passwordHash, spec.firstName, spec.lastName, spec.role, spec.status);
        return new TestUser(id, spec.email, spec.role, spec.rawPassword);
    }

    // ── Product ─────────────────────────────────────────────────────────────────

    public TestProduct createProduct() {
        return createProduct(spec -> { });
    }

    /**
     * Создаёт валидный товар каталога: свежие категорию и бренд, сам товар и один
     * вариант (цвет + объём) с ценой. Возвращает id товара, его sku и цену варианта.
     */
    public TestProduct createProduct(Consumer<ProductSpec> customizer) {
        ProductSpec spec = new ProductSpec();
        customizer.accept(spec);

        Long categoryId = jdbc.queryForObject(
                "INSERT INTO categories (slug, sort_order, is_active) VALUES (?, 0, true) RETURNING id",
                Long.class, "cat-" + token());
        Long brandId = jdbc.queryForObject(
                "INSERT INTO brands (slug, name, is_active) VALUES (?, ?, true) RETURNING id",
                Long.class, "brand-" + token(), "Test Brand");

        Long productId = jdbc.queryForObject("""
                        INSERT INTO products
                            (category_id, brand_id, sku, paint_type, usage_area, finish, base_type,
                             is_active, is_featured)
                        VALUES (?, ?, ?, ?, ?, ?, ?, true, false)
                        RETURNING id""",
                Long.class,
                categoryId, brandId, spec.sku, spec.paintType, spec.usageArea, spec.finish, spec.baseType);

        jdbc.update("""
                        INSERT INTO product_variants
                            (product_id, sku, color_code, volume_liters, price, is_active)
                        VALUES (?, ?, ?, ?, ?, true)""",
                productId, spec.sku + "-V", spec.colorCode, spec.volumeLiters, spec.price);

        return new TestProduct(productId, spec.sku, spec.price);
    }

    // ── Specs (мутабельные, с дефолтами) ────────────────────────────────────────

    public static final class UserSpec {
        public String email = "user-" + token() + "@tzevabest.test";
        // phone уникален (uq_users_phone); по умолчанию null, чтобы несколько пользователей
        // в одном тесте не конфликтовали. Переопределяется при необходимости.
        public String phone = null;
        public String passwordHash = "$2a$10$testonlyplaceholderhashvaluexxxxxxxxxxxxxxxxxxxxxxxx";
        public String rawPassword = "password";
        public String firstName = "Test";
        public String lastName = "User";
        public String role = "CUSTOMER";
        public String status = "ACTIVE";
    }

    public static final class ProductSpec {
        public String sku = "SKU-" + token();
        public String paintType = "INTERIOR";
        public String usageArea = "INDOOR";
        public String finish = "MATTE";
        public String baseType = "WATER";
        public String colorCode = "RAL9016";
        public BigDecimal volumeLiters = new BigDecimal("1.00");
        public BigDecimal price = new BigDecimal("99.90");
    }

    // ── Возвращаемые представления созданных сущностей ──────────────────────────

    public record TestUser(long id, String email, String role, String rawPassword) { }

    public record TestProduct(long id, String sku, BigDecimal price) { }
}
