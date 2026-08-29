package com.tzevabest.service.service;

import com.tzevabest.admin.service.AuditService;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.common.web.security.CurrentUser;
import com.tzevabest.service.dto.PricingRuleUpdate;
import com.tzevabest.service.dto.PricingRuleView;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Админ-управление тарифами покраски (T-11.2). Изменения — в audit_log. */
@Service
@RequiredArgsConstructor
public class PricingAdminService {

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public List<PricingRuleView> list() {
        return jdbc.query("""
                select id, property_type, work_type, price_per_sqm_min, price_per_sqm_max, min_order_price, is_active
                from service_pricing_rules order by property_type, work_type""",
                (rs, n) -> new PricingRuleView(rs.getLong("id"), rs.getString("property_type"),
                        rs.getString("work_type"), rs.getBigDecimal("price_per_sqm_min"),
                        rs.getBigDecimal("price_per_sqm_max"), rs.getBigDecimal("min_order_price"),
                        rs.getBoolean("is_active")));
    }

    @Transactional
    public PricingRuleView update(long id, PricingRuleUpdate req) {
        if (req.pricePerSqmMax().compareTo(req.pricePerSqmMin()) < 0) {
            throw new com.tzevabest.common.web.error.ValidationException("service.pricing.range_invalid");
        }
        int updated = jdbc.update("""
                update service_pricing_rules set price_per_sqm_min = ?, price_per_sqm_max = ?,
                        min_order_price = ?, is_active = coalesce(?, is_active), updated_at = now()
                where id = ?""",
                req.pricePerSqmMin(), req.pricePerSqmMax(), req.minOrderPrice(), req.active(), id);
        if (updated == 0) {
            throw new NotFoundException("service.pricing.rule_not_found");
        }
        audit.record(currentUser.optional().map(p -> p.userId()).orElse(null), currentUser.ip(),
                "pricing_rule.update", "service_pricing_rule", id,
                Map.of("price_min", req.pricePerSqmMin(), "price_max", req.pricePerSqmMax()));
        return list().stream().filter(r -> r.id() == id).findFirst().orElseThrow();
    }
}
