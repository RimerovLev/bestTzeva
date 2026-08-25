package com.tzevabest.common.web.security;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Проверка владения ресурсом для {@code @PreAuthorize("@ownership.owns('address', #id)")}.
 * Для {@code ADMIN} и {@code MANAGER} проверка пропускается (полный доступ).
 */
@Component("ownership")
public class ResourceOwnership {

    private final CurrentUser currentUser;
    private final Map<String, OwnershipProvider> providers;

    public ResourceOwnership(CurrentUser currentUser, List<OwnershipProvider> providers) {
        this.currentUser = currentUser;
        this.providers = providers.stream()
                .collect(Collectors.toMap(OwnershipProvider::resource, Function.identity()));
    }

    public boolean owns(String resource, long resourceId) {
        if (isPrivileged()) {
            return true;
        }
        OwnershipProvider provider = providers.get(resource);
        if (provider == null) {
            return false;
        }
        long me = currentUser.id();
        return provider.ownerId(resourceId).map(owner -> owner == me).orElse(false);
    }

    private boolean isPrivileged() {
        String role = currentUser.role();
        return "ADMIN".equals(role) || "MANAGER".equals(role);
    }
}
