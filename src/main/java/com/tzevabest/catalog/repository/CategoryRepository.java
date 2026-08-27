package com.tzevabest.catalog.repository;

import com.tzevabest.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** CRUD категорий. Древовидное чтение с подсчётами делается отдельно (JdbcTemplate) без N+1. */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countByParent_Id(Long parentId);

    List<Category> findByParent_Id(Long parentId);

    @Query(value = "select count(*) from products where category_id = :id", nativeQuery = true)
    long countProductsInCategory(@Param("id") long id);
}
