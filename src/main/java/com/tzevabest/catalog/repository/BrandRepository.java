package com.tzevabest.catalog.repository;

import com.tzevabest.catalog.domain.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, Long> {
}
