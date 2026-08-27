package com.tzevabest.catalog.repository;

import com.tzevabest.catalog.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProduct_Id(Long productId);

    Optional<ProductVariant> findBySku(String sku);

    long countByProduct_IdAndActiveTrue(Long productId);
}
