package com.tzevabest.iam.repository;

import com.tzevabest.iam.domain.User;
import com.tzevabest.iam.domain.UserRole;
import com.tzevabest.iam.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    /** Активные сотрудники заданных ролей — кандидаты в исполнители заявок. */
    List<User> findByStatusAndRoleInOrderByFirstNameAscLastNameAscIdAsc(UserStatus status, Collection<UserRole> roles);
}
