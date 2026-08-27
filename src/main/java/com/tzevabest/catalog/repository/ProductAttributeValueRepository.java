package com.tzevabest.catalog.repository;

import com.tzevabest.catalog.domain.ProductAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, Long> {

    @Transactional
    void deleteByProduct_Id(Long productId);
}
