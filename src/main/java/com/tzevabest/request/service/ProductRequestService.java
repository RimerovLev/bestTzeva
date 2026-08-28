package com.tzevabest.request.service;

import com.tzevabest.common.util.PhoneNormalizer;
import com.tzevabest.common.web.error.ValidationException;
import com.tzevabest.iam.service.ConsentService;
import com.tzevabest.request.domain.ProductRequest;
import com.tzevabest.request.domain.ProductRequestItem;
import com.tzevabest.request.domain.ProductRequestSource;
import com.tzevabest.request.domain.ProductRequestStatus;
import com.tzevabest.request.dto.ProductRequestCreate;
import com.tzevabest.request.dto.ProductRequestItemInput;
import com.tzevabest.request.dto.ProductRequestItemView;
import com.tzevabest.request.dto.ProductRequestResponse;
import com.tzevabest.request.event.ProductRequestCreatedEvent;
import com.tzevabest.request.repository.ProductRequestItemRepository;
import com.tzevabest.request.repository.ProductRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Приём заявки на товар от гостя. Согласие обязательно; цены и снимки берутся из каталога на сервере,
 * а не из запроса; варианты должны быть активны. Антиспам — honeypot (здесь) + rate limit на
 * контроллере. После создания публикуется событие (подтверждение клиенту, уведомление менеджерам).
 */
@Service
@RequiredArgsConstructor
public class ProductRequestService {

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    private final ProductRequestRepository requests;
    private final ProductRequestItemRepository items;
    private final ConsentService consents;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;

    @Value("${app.privacy.policy-version}")
    private String policyVersion;

    @Transactional
    public ProductRequestResponse create(ProductRequestCreate req, String ip) {
        if (req.website() != null && !req.website().isBlank()) {
            throw new ValidationException("request.spam"); // honeypot заполнен — бот
        }
        if (!req.consent()) {
            throw new ValidationException("request.consent_required");
        }
        String phone = PhoneNormalizer.normalize(req.customerPhone());

        ProductRequest request = new ProductRequest();
        request.setRequestNumber(nextNumber());
        request.setCustomerName(req.customerName());
        request.setCustomerPhone(phone);
        request.setCustomerEmail(blankToNull(req.customerEmail()));
        request.setComment(blankToNull(req.comment()));
        request.setPreferredContactTime(blankToNull(req.preferredContactTime()));
        request.setStatus(ProductRequestStatus.NEW);
        request.setSource(ProductRequestSource.SITE_FORM);
        request.setConsentGivenAt(Instant.now());
        request.setPolicyVersion(policyVersion);
        requests.save(request);

        // Дедуп позиций, суммируя qty (иначе резерв по такой заявке встанет дважды).
        Map<Long, Integer> byVariant = new LinkedHashMap<>();
        for (ProductRequestItemInput input : req.items()) {
            byVariant.merge(input.variantId(), input.qty(), Integer::sum);
        }
        // Один запрос вместо N: только активные варианты активных товаров.
        Map<Long, VariantSnapshot> snapshots = lookupActiveVariants(byVariant.keySet());
        if (!snapshots.keySet().containsAll(byVariant.keySet())) {
            throw new ValidationException("request.variant_unavailable");
        }

        List<ProductRequestItemView> views = new ArrayList<>();
        List<ProductRequestItem> toSave = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : byVariant.entrySet()) {
            long variantId = e.getKey();
            int qty = e.getValue();
            VariantSnapshot snap = snapshots.get(variantId);
            ProductRequestItem item = new ProductRequestItem();
            item.setRequest(request);
            item.setVariantId(variantId);
            item.setQty(qty);
            item.setProductNameSnapshot(snap.name());
            item.setSkuSnapshot(snap.sku());
            item.setColorSnapshot(snap.color());
            item.setVolumeLitersSnapshot(snap.volume());
            item.setPriceSnapshot(snap.price()); // цена с сервера, не из запроса
            toSave.add(item);
            total = total.add(snap.price().multiply(BigDecimal.valueOf(qty)));
            views.add(new ProductRequestItemView(variantId, qty,
                    snap.name(), snap.sku(), snap.color(), snap.volume(), snap.price()));
        }
        items.saveAll(toSave); // пакетная вставка

        consents.recordDataProcessing(null, phone, ip, "product_request");
        jdbc.update("insert into product_request_history (request_id, from_status, to_status) values (?, null, 'NEW')",
                request.getId());

        events.publishEvent(new ProductRequestCreatedEvent(request.getId(), request.getRequestNumber(),
                phone, request.getCustomerEmail(), "he"));

        return new ProductRequestResponse(request.getId(), request.getRequestNumber(),
                request.getStatus(), total, views);
    }

    /** Снимки только активных вариантов активных товаров, одним запросом. Отсутствие id = недоступен. */
    private Map<Long, VariantSnapshot> lookupActiveVariants(java.util.Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Map<Long, VariantSnapshot> result = new java.util.HashMap<>();
        jdbc.query("select v.id, v.price, v.color_code, v.volume_liters, v.sku, "
                + "coalesce(he.name, en.name, v.sku) as name "
                + "from product_variants v join products p on p.id = v.product_id "
                + "left join product_translations he on he.product_id = p.id and he.locale = 'he' "
                + "left join product_translations en on en.product_id = p.id and en.locale = 'en' "
                + "where v.is_active and p.is_active and v.id in (" + placeholders + ")",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> result.put(rs.getLong("id"),
                        new VariantSnapshot(rs.getBigDecimal("price"), rs.getString("color_code"),
                                rs.getBigDecimal("volume_liters"), rs.getString("sku"), rs.getString("name"))),
                ids.toArray());
        return result;
    }

    /**
     * Номер заявки из последовательности (R-46): атомарно и без коллизий, в отличие от случайного
     * числа с проверкой. Дата — информационная; уникальность даёт nextval. Package-private для теста.
     */
    String nextNumber() {
        Long seq = jdbc.queryForObject("select nextval('product_request_number_seq')", Long.class);
        return "PR-" + LocalDate.now(ZoneOffset.UTC).format(YYMMDD) + "-" + String.format("%04d", seq);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private record VariantSnapshot(BigDecimal price, String color, BigDecimal volume, String sku, String name) {
    }
}
