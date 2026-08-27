package com.tzevabest.catalog.repository;

import com.tzevabest.catalog.domain.ProductAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Long> {

    Optional<ProductAttribute> findByCode(String code);
}
