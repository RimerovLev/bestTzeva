package com.tzevabest.request.repository;

import com.tzevabest.request.domain.ProductRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRequestItemRepository extends JpaRepository<ProductRequestItem, Long> {
}
