package com.tzevabest.iam.service;

import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.iam.dto.AddressResponse;
import com.tzevabest.iam.repository.UserAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final UserAddressRepository addresses;

    public AddressResponse get(long id) {
        return addresses.findById(id)
                .map(a -> new AddressResponse(a.getId(), a.getCity(), a.getStreet(), a.getHouse(), a.isDefault()))
                .orElseThrow(() -> new NotFoundException("common.resource.not_found"));
    }
}
