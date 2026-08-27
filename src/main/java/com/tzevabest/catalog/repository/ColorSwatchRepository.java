package com.tzevabest.catalog.repository;

import com.tzevabest.catalog.domain.ColorSwatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ColorSwatchRepository extends JpaRepository<ColorSwatch, Long> {
    Optional<ColorSwatch> findByCode(String code);
    boolean existsByCode(String code);
}
