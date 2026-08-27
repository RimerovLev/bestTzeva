package com.tzevabest.catalog.repository;

import com.tzevabest.catalog.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    @Query(value = "select exists(select 1 from order_items oi "
            + "join product_variants pv on pv.id = oi.variant_id where pv.product_id = :productId)",
            nativeQuery = true)
    boolean hasOrderHistory(@Param("productId") long productId);
}
