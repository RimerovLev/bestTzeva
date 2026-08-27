package com.tzevabest.request.repository;

import com.tzevabest.request.domain.ProductRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRequestRepository extends JpaRepository<ProductRequest, Long> {

    boolean existsByRequestNumber(String requestNumber);
}
