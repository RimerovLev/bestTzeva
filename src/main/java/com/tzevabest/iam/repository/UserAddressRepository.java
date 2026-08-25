package com.tzevabest.iam.repository;

import com.tzevabest.iam.domain.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    /** Возвращает user_id владельца адреса без загрузки самого пользователя. */
    @Query("select a.user.id from UserAddress a where a.id = :id")
    Optional<Long> findOwnerId(@Param("id") long id);
}
