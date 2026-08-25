package com.tzevabest.admin.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Административные эндпоинты по зонам ответственности (см. docs/roles.md). Пока заглушки,
 * закрытые @PreAuthorize согласно матрице прав; реальная логика появится в модулях-владельцах,
 * сохранив те же ограничения. Вся зона /api/v1/admin/** дополнительно закрыта цепочкой (не CUSTOMER).
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('SUPPORT','WAREHOUSE','MANAGER','ADMIN')")
    public void readOrders() {
    }

    @PutMapping("/inventory")
    @PreAuthorize("hasAnyRole('WAREHOUSE','MANAGER','ADMIN')")
    public void updateInventory() {
    }

    @PutMapping("/service-requests/{id}/status")
    @PreAuthorize("hasAnyRole('SUPPORT','MANAGER','ADMIN')")
    public void changeServiceRequestStatus(@PathVariable long id) {
    }

    @PutMapping("/catalog")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public void updateCatalog() {
    }

    @GetMapping("/reports/finance")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public void financeReport() {
    }
}
