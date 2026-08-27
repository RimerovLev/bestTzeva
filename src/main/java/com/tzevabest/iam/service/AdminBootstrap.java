package com.tzevabest.iam.service;

import com.tzevabest.iam.domain.User;
import com.tzevabest.iam.domain.UserRole;
import com.tzevabest.iam.domain.UserStatus;
import com.tzevabest.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Первый администратор. После закрытия публичной регистрации (T-16.2) в систему иначе некому войти,
 * поэтому при ПУСТОЙ таблице users и заданных переменных {@code app.bootstrap.admin-email/password}
 * создаётся один ADMIN. Если переменные не заданы или пользователи уже есть — компонент ничего не
 * делает (в т.ч. в тестах, где переменные пусты).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin-email:}")
    private String adminEmail;

    @Value("${app.bootstrap.admin-password:}")
    private String adminPassword;

    @Value("${app.auth.default-locale:he}")
    private String defaultLocale;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            return; // не настроено
        }
        if (users.count() > 0) {
            return; // база не пуста — не вмешиваемся
        }
        User admin = new User();
        admin.setEmail(adminEmail.trim().toLowerCase(Locale.ROOT));
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFirstName("Admin");
        admin.setLastName("Admin");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setLocale(defaultLocale);
        users.save(admin);
        log.info("Bootstrap admin created for empty users table: {}", admin.getEmail());
    }
}
