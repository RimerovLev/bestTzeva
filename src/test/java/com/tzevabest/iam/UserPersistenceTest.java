package com.tzevabest.iam;

import com.tzevabest.iam.domain.User;
import com.tzevabest.iam.domain.UserAddress;
import com.tzevabest.iam.domain.UserRole;
import com.tzevabest.iam.domain.UserStatus;
import com.tzevabest.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Персистентность сущностей поверх Flyway-схемы: генерация id и аудита времени БД,
 * работа FK и триггера {@code set_updated_at}.
 */
class UserPersistenceTest extends IntegrationTest {

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
    void persists_user_and_address_with_db_generated_fields() {
        Long userId = tx.execute(status -> {
            User user = newUser();
            em.persist(user);
            em.flush();

            assertThat(user.getId()).isNotNull();
            assertThat(user.getCreatedAt()).isNotNull();
            assertThat(user.getUpdatedAt()).isNotNull();

            UserAddress address = new UserAddress();
            address.setUser(user);
            address.setCity("Tel Aviv");
            address.setStreet("Herzl");
            address.setHouse("1");
            address.setDefault(true);
            em.persist(address);
            em.flush();

            assertThat(address.getId()).isNotNull();
            assertThat(address.getCreatedAt()).isNotNull();
            return user.getId();
        });

        assertThat(userId).isNotNull();
    }

    @Test
    void update_trigger_advances_updated_at() throws InterruptedException {
        Long userId = tx.execute(status -> {
            User user = newUser();
            em.persist(user);
            em.flush();
            return user.getId();
        });

        Instant createdAt = tx.execute(status ->
                em.find(User.class, userId).getCreatedAt());

        // now() постоянна внутри транзакции — обновляем в отдельной, чуть позже по времени.
        Thread.sleep(15);

        Instant updatedAt = tx.execute(status -> {
            User user = em.find(User.class, userId);
            user.setLastName("Changed");
            em.flush();
            return user.getUpdatedAt();
        });

        assertThat(updatedAt).isAfter(createdAt);
    }

    private static User newUser() {
        User user = new User();
        user.setEmail("persist-" + UUID.randomUUID() + "@tzevabest.test");
        user.setPasswordHash("$2a$10$placeholderplaceholderplaceholderplaceholderpla");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
