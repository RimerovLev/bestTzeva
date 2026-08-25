package com.tzevabest.common.web.security;

import java.util.Optional;

/**
 * Провайдер владения ресурсом. Реализуется модулем-владельцем (напр. iam — адреса,
 * order — заказы) и регистрируется как бин; {@link ResourceOwnership} их собирает.
 */
public interface OwnershipProvider {

    /** Тип ресурса, напр. {@code "address"}, {@code "order"}. */
    String resource();

    /** id владельца ресурса (user_id) или пусто, если ресурс не найден. */
    Optional<Long> ownerId(long resourceId);
}
