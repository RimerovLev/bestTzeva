package com.tzevabest.inventory.web;

import com.tzevabest.common.dto.PageResponse;
import com.tzevabest.common.web.ApiPage;
import com.tzevabest.common.web.PageQuery;
import com.tzevabest.inventory.dto.InventoryListItem;
import com.tzevabest.inventory.dto.InventoryStatus;
import com.tzevabest.inventory.dto.LowStockItem;
import com.tzevabest.inventory.dto.ReceiptRequest;
import com.tzevabest.inventory.dto.StocktakeRequest;
import com.tzevabest.inventory.dto.TransferRequest;
import com.tzevabest.inventory.dto.VariantMovements;
import com.tzevabest.inventory.dto.WriteOffRequest;
import com.tzevabest.inventory.service.InventoryAdminService;
import com.tzevabest.inventory.service.InventoryXlsxExporter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Складское админ-API остатков и движений. Доступ — WAREHOUSE/MANAGER/ADMIN (SUPPORT не допускается). */
@RestController
@RequestMapping("/admin/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('WAREHOUSE','MANAGER','ADMIN')")
public class AdminInventoryController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final InventoryAdminService service;
    private final InventoryXlsxExporter exporter;

    @GetMapping
    public PageResponse<InventoryListItem> list(
            @RequestParam(name = "warehouse_id", required = false) Long warehouseId,
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(name = "brand_id", required = false) Long brandId,
            @RequestParam(required = false) InventoryStatus status,
            @RequestParam(name = "q", required = false) String query,
            @ApiPage(sort = {"sku", "qty", "available", "reserved"}) PageQuery page) {
        return service.list(warehouseId, categoryId, brandId, status, query, page);
    }

    @PostMapping("/receipt")
    public Map<String, Object> receipt(@Valid @RequestBody ReceiptRequest request) {
        return Map.of("received_lines", service.receipt(request));
    }

    @PostMapping("/write-off")
    public void writeOff(@Valid @RequestBody WriteOffRequest request) {
        service.writeOff(request);
    }

    @PostMapping("/stocktake")
    public void stocktake(@Valid @RequestBody StocktakeRequest request) {
        service.stocktake(request);
    }

    @PostMapping("/transfer")
    public void transfer(@Valid @RequestBody TransferRequest request) {
        service.transfer(request);
    }

    @GetMapping("/{variantId}/movements")
    public VariantMovements movements(
            @PathVariable long variantId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @ApiPage PageQuery page) {
        return service.movements(variantId, type, from, to, page);
    }

    @GetMapping("/low-stock")
    public List<LowStockItem> lowStock() {
        return service.lowStock();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(name = "warehouse_id", required = false) Long warehouseId,
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(name = "brand_id", required = false) Long brandId,
            @RequestParam(required = false) InventoryStatus status,
            @RequestParam(name = "q", required = false) String query) {
        byte[] body = exporter.export(service.forExport(warehouseId, categoryId, brandId, status, query));
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("inventory.xlsx").build().toString())
                .body(body);
    }
}
