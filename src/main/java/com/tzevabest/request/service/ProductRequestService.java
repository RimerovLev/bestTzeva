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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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

        List<ProductRequestItemView> views = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ProductRequestItemInput input : req.items()) {
            VariantSnapshot snap = lookupActiveVariant(input.variantId());
            ProductRequestItem item = new ProductRequestItem();
            item.setRequest(request);
            item.setVariantId(input.variantId());
            item.setQty(input.qty());
            item.setProductNameSnapshot(snap.name());
            item.setSkuSnapshot(snap.sku());
            item.setColorSnapshot(snap.color());
            item.setVolumeLitersSnapshot(snap.volume());
            item.setPriceSnapshot(snap.price()); // цена с сервера, не из запроса
            items.save(item);
            total = total.add(snap.price().multiply(BigDecimal.valueOf(input.qty())));
            views.add(new ProductRequestItemView(input.variantId(), input.qty(),
                    snap.name(), snap.sku(), snap.color(), snap.volume(), snap.price()));
        }

        consents.recordDataProcessing(null, phone, ip, "product_request");
        jdbc.update("insert into product_request_history (request_id, from_status, to_status) values (?, null, 'NEW')",
                request.getId());

        events.publishEvent(new ProductRequestCreatedEvent(request.getId(), request.getRequestNumber(),
                phone, request.getCustomerEmail(), "he"));

        return new ProductRequestResponse(request.getId(), request.getRequestNumber(),
                request.getStatus(), total, views);
    }

    private VariantSnapshot lookupActiveVariant(long variantId) {
        try {
            return jdbc.queryForObject("""
                    select v.price, v.color_code, v.volume_liters, v.sku, v.is_active as v_active,
                           p.is_active as p_active,
                           coalesce(he.name, en.name, v.sku) as name
                    from product_variants v
                    join products p on p.id = v.product_id
                    left join product_translations he on he.product_id = p.id and he.locale = 'he'
                    left join product_translations en on en.product_id = p.id and en.locale = 'en'
                    where v.id = ?
                    """, (rs, i) -> new VariantSnapshot(
                            rs.getBigDecimal("price"), rs.getString("color_code"), rs.getBigDecimal("volume_liters"),
                            rs.getString("sku"), rs.getBoolean("v_active"), rs.getBoolean("p_active"),
                            rs.getString("name")), variantId);
        } catch (EmptyResultDataAccessException e) {
            throw new ValidationException("request.variant_unavailable");
        }
    }

    private String nextNumber() {
        String number;
        do {
            number = "PR-" + LocalDate.now(ZoneOffset.UTC).format(YYMMDD) + "-"
                    + String.format("%04d", ThreadLocalRandom.current().nextInt(10_000));
        } while (requests.existsByRequestNumber(number));
        return number;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private record VariantSnapshot(BigDecimal price, String color, BigDecimal volume, String sku,
                                   boolean variantActive, boolean productActive, String name) {
        VariantSnapshot {
            if (!variantActive || !productActive) {
                throw new ValidationException("request.variant_unavailable");
            }
        }
    }
}
