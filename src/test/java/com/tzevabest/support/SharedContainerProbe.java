package com.tzevabest.support;

import org.testcontainers.containers.GenericContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Кросс-классовая проверка singleton-контейнера: первый вызвавший фиксирует id
 * контейнера, последующие сверяются с ним. Если бы разные классы поднимали разные
 * контейнеры, id отличались бы и проверка упала бы.
 */
final class SharedContainerProbe {

    private static volatile String firstContainerId;

    private SharedContainerProbe() {
    }

    static synchronized void assertSameContainer(GenericContainer<?> container) {
        assertThat(container.isRunning()).isTrue();
        String id = container.getContainerId();
        if (firstContainerId == null) {
            firstContainerId = id;
        } else {
            assertThat(id)
                    .as("все классы тестов должны использовать один и тот же контейнер")
                    .isEqualTo(firstContainerId);
        }
    }
}
