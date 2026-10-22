package com.tzevabest.common.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Не даёт сочетать {@code prod} с {@code local}/{@code test} (перепроверка аудита, п.1 LOW).
 * При активных {@code prod,local} (или {@code prod,test}) сработала бы открытая dev-цепочка Security,
 * а {@link ProdSecretsGuard} (он {@code !local & !test}) бы не инициализировался — то есть prod поднялся
 * бы с открытым API и dev-секретами. Этот guard активен всегда и роняет старт на такой комбинации.
 */
@Component
@RequiredArgsConstructor
public class ProfileSanityGuard {

    private final Environment environment;

    @PostConstruct
    void verify() {
        Set<String> active = Set.of(environment.getActiveProfiles());
        if (active.contains("prod") && (active.contains("local") || active.contains("test"))) {
            throw new IllegalStateException(
                    "Profile 'prod' must not be combined with 'local'/'test' — активные профили: " + active);
        }
    }
}
