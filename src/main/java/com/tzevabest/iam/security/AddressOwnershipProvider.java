package com.tzevabest.iam.security;

import com.tzevabest.common.web.security.OwnershipProvider;
import com.tzevabest.iam.repository.UserAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Провайдер владения адресами (iam). */
@Component
@RequiredArgsConstructor
public class AddressOwnershipProvider implements OwnershipProvider {

    private final UserAddressRepository addresses;

    @Override
    public String resource() {
        return "address";
    }

    @Override
    public Optional<Long> ownerId(long resourceId) {
        return addresses.findOwnerId(resourceId);
    }
}
