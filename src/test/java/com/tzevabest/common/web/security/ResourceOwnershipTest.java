package com.tzevabest.common.web.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * R-07: {@code owns()} не должен бросать исключение из тела SpEL для анонима (иначе 500 в обход
 * обычного пути отказа) — возвращает false, а 401 отдаст Security.
 */
class ResourceOwnershipTest {

    private final OwnershipProvider addressProvider = new OwnershipProvider() {
        @Override public String resource() {
            return "address";
        }

        @Override public Optional<Long> ownerId(long resourceId) {
            return resourceId == 5L ? Optional.of(42L) : Optional.empty();
        }
    };

    private final ResourceOwnership ownership =
            new ResourceOwnership(new CurrentUser(), List.of(addressProvider));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymous_owns_returns_false_without_throwing() {
        SecurityContextHolder.clearContext();
        assertThatCode(() -> assertThat(ownership.owns("address", 5L)).isFalse())
                .doesNotThrowAnyException();
    }

    @Test
    void owner_is_allowed_and_non_owner_denied() {
        authenticate(42L, "CUSTOMER");
        assertThat(ownership.owns("address", 5L)).isTrue();   // владелец
        assertThat(ownership.owns("address", 6L)).isFalse();  // не владелец
    }

    @Test
    void admin_and_manager_are_privileged() {
        authenticate(99L, "ADMIN");
        assertThat(ownership.owns("address", 6L)).isTrue();
        authenticate(98L, "MANAGER");
        assertThat(ownership.owns("address", 6L)).isTrue();
    }

    private static void authenticate(long userId, String role) {
        AuthPrincipal principal = new AuthPrincipal(userId, role, "u@x.test", "he");
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
