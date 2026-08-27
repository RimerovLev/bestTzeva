package com.tzevabest.catalog.repository;

import com.tzevabest.catalog.domain.ProductTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ProductTranslationRepository extends JpaRepository<ProductTranslation, Long> {

    @Transactional
    void deleteByProduct_Id(Long productId);
}
