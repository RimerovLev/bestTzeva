package com.tzevabest.catalog;

import com.tzevabest.catalog.domain.BaseType;
import com.tzevabest.catalog.domain.Brand;
import com.tzevabest.catalog.domain.Category;
import com.tzevabest.catalog.domain.Finish;
import com.tzevabest.catalog.domain.MediaType;
import com.tzevabest.catalog.domain.PaintType;
import com.tzevabest.catalog.domain.Product;
import com.tzevabest.catalog.domain.ProductMedia;
import com.tzevabest.catalog.domain.ProductVariant;
import com.tzevabest.catalog.domain.UsageArea;
import com.tzevabest.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Связи сущностей каталога, round-trip jsonb и check-ограничение владельца медиа. */
class CatalogPersistenceTest extends IntegrationTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private PlatformTransactionManager txManager;

    private TransactionTemplate tx;

    @BeforeEach
    void initTx() {
        tx = new TransactionTemplate(txManager);
    }

    @Test
    void product_graph_persists_with_relations_and_jsonb() {
        Long productId = tx.execute(status -> {
            Product product = persistProductGraph();

            ProductMedia media = new ProductMedia();
            media.setProduct(product);
            media.setUrl("https://cdn.tzevabest.test/p.png");
            media.setType(MediaType.IMAGE);
            media.setPrimary(true);
            media.setAltTranslations(Map.of("he", "תמונה", "en", "image"));
            em.persist(media);
            em.flush();
            return product.getId();
        });

        tx.executeWithoutResult(status -> {
            Product loaded = em.find(Product.class, productId);
            assertThat(loaded.getCategory()).isNotNull();
            assertThat(loaded.getBrand().getName()).isEqualTo("Test Brand");

            ProductVariant variant = em.createQuery(
                            "select v from ProductVariant v where v.product.id = :id", ProductVariant.class)
                    .setParameter("id", productId).getSingleResult();
            assertThat(variant.getPrice()).isEqualByComparingTo("49.90");

            ProductMedia media = em.createQuery(
                            "select m from ProductMedia m where m.product.id = :id", ProductMedia.class)
                    .setParameter("id", productId).getSingleResult();
            assertThat(media.getAltTranslations()).containsEntry("he", "תמונה").containsEntry("en", "image");
        });
    }

    @Test
    void product_media_requires_exactly_one_owner() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            Product product = persistProductGraph();
            ProductVariant variant = em.createQuery(
                            "select v from ProductVariant v where v.product.id = :id", ProductVariant.class)
                    .setParameter("id", product.getId()).getSingleResult();

            ProductMedia media = new ProductMedia();
            media.setProduct(product);   // оба владельца заполнены — нарушает ck_product_media_owner
            media.setVariant(variant);
            media.setUrl("https://cdn.tzevabest.test/bad.png");
            media.setType(MediaType.IMAGE);
            em.persist(media);
            em.flush();
        })).isInstanceOf(Exception.class);
    }

    private Product persistProductGraph() {
        String token = UUID.randomUUID().toString().substring(0, 8);

        Category category = new Category();
        category.setSlug("cat-" + token);
        em.persist(category);

        Brand brand = new Brand();
        brand.setSlug("brand-" + token);
        brand.setName("Test Brand");
        em.persist(brand);

        Product product = new Product();
        product.setCategory(category);
        product.setBrand(brand);
        product.setSku("SKU-" + token);
        product.setPaintType(PaintType.INTERIOR);
        product.setUsageArea(UsageArea.INDOOR);
        product.setFinish(Finish.MATTE);
        product.setBaseType(BaseType.WATER);
        em.persist(product);

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku("SKU-" + token + "-V");
        variant.setColorCode("RAL9016");
        variant.setVolumeLiters(new BigDecimal("1.00"));
        variant.setPrice(new BigDecimal("49.90"));
        em.persist(variant);
        em.flush();

        return product;
    }
}
