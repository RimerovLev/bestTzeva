package com.tzevabest.common.spam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.OptionalLong;

/**
 * Общая защита публичных форм (T-11.1.1): honeypot, минимальное время заполнения по подписанному
 * токену формы, блок-лист IP/телефонов и общий журнал отсечённых попыток. Один экземпляр на обе формы
 * (заявки на товар и на покраску) — не копируется в каждый модуль.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SpamGuard {

    /** Причины отсечения — совпадают с ck_spam_drops_reason. */
    public static final String HONEYPOT = "honeypot";
    public static final String TOO_FAST = "too_fast";
    public static final String BLOCKED = "blocked";

    private final JdbcTemplate jdbc;
    private final FormTokenService formTokens;

    @Value("${app.spam.min-fill-seconds:3}")
    private long minFillSeconds;

    /** Заполнен ли honeypot (скрытое поле). */
    public boolean isHoneypot(String honeypotValue) {
        return honeypotValue != null && !honeypotValue.isBlank();
    }

    /** Отправлено ли слишком быстро (токен валиден и моложе порога). Нет токена → не судим. */
    public boolean isTooFast(String formToken) {
        OptionalLong age = formTokens.ageSeconds(formToken);
        return age.isPresent() && age.getAsLong() < minFillSeconds;
    }

    /** Заблокированы ли IP или телефон. */
    public boolean isBlocked(String ip, String phone) {
        Integer c = jdbc.queryForObject(
                "select count(*) from block_list where (kind = 'IP' and value = ?) or (kind = 'PHONE' and value = ?)",
                Integer.class, ip, phone);
        return c != null && c > 0;
    }

    /**
     * Причина тихого отсечения формы, если есть (honeypot / too_fast / blocked), иначе null. Порядок:
     * honeypot → too_fast → блок-лист.
     */
    public String dropReason(String honeypotValue, String formToken, String ip, String phone) {
        if (isHoneypot(honeypotValue)) {
            return HONEYPOT;
        }
        if (isTooFast(formToken)) {
            return TOO_FAST;
        }
        if (isBlocked(ip, phone)) {
            return BLOCKED;
        }
        return null;
    }

    /** Записать отсечённую попытку в общий журнал (для анализа, не теряем). */
    public void recordDrop(String form, String ip, String phone, String reason) {
        jdbc.update("insert into spam_drops (form, ip, phone, reason) values (?, ?, ?, ?)",
                form, ip, phone, reason);
        log.info("Spam drop: form={} reason={} ip={}", form, reason, ip);
    }
}
