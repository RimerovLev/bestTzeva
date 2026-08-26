package com.tzevabest.service;

import com.tzevabest.service.domain.PropertyType;
import com.tzevabest.service.domain.RequestSource;
import com.tzevabest.service.domain.ServiceRequest;
import com.tzevabest.service.domain.WorkType;
import com.tzevabest.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Персистентность заявки на услугу, включая набор видов работ (@ElementCollection). */
class ServiceRequestPersistenceTest extends IntegrationTest {

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
    void persists_request_with_work_types() {
        Long id = tx.execute(status -> {
            ServiceRequest request = new ServiceRequest();
            request.setRequestNumber("SR-" + UUID.randomUUID().toString().substring(0, 8));
            request.setCustomerName("Test Customer");
            request.setCustomerPhone("+972500000000");
            request.setPropertyType(PropertyType.APARTMENT);
            request.setSource(RequestSource.SITE_FORM);
            request.setConsentGivenAt(java.time.Instant.now());
            request.setWorkTypes(EnumSet.of(WorkType.WALLS, WorkType.CEILING));
            em.persist(request);
            em.flush();
            return request.getId();
        });

        tx.executeWithoutResult(status -> {
            ServiceRequest loaded = em.find(ServiceRequest.class, id);
            assertThat(loaded.getStatus().name()).isEqualTo("NEW");
            assertThat(loaded.getWorkTypes())
                    .containsExactlyInAnyOrder(WorkType.WALLS, WorkType.CEILING);
        });
    }
}
