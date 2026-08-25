package com.tzevabest.iam.web;

import com.tzevabest.iam.dto.AddressResponse;
import com.tzevabest.iam.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Клиентские адреса. Доступ — владельцу (или ADMIN/MANAGER) через @ownership. */
@RestController
@RequestMapping("/me/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping("/{id}")
    @PreAuthorize("@ownership.owns('address', #id)")
    public AddressResponse get(@PathVariable long id) {
        return addressService.get(id);
    }
}
