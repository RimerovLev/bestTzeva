package com.tzevabest.catalog.repository;

import com.tzevabest.catalog.domain.CategoryTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/** Переводы категорий. При обновлении переводы заменяются целиком (delete + insert). */
public interface CategoryTranslationRepository extends JpaRepository<CategoryTranslation, Long> {

    @Transactional
    void deleteByCategory_Id(Long categoryId);
}
