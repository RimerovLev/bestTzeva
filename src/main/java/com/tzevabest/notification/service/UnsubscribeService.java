package com.tzevabest.notification.service;

import com.tzevabest.common.web.error.ValidationException;
import com.tzevabest.iam.service.ConsentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Отписка от рассылок (T-12.3). Ссылка в информационных письмах ведёт на эндпоинт с подписанным
 * токеном; отписка пишется в consents как отзыв MARKETING-согласия. Транзакционные письма (статус
 * заявки, сброс пароля) не отписываются. Проверка подавления учитывает флаг шаблона и отзыв.
 */
@Service
@RequiredArgsConstructor
public class UnsubscribeService {

    private final ConsentService consents;
    private final JdbcTemplate jdbc;

    @Value("${app.notification.signing-secret}")
    private String secret;

    @Value("${app.web.base-url:http://localhost:8080}")
    private String baseUrl;

    /** Ссылка отписки для получателя (email). */
    public String unsubscribeLink(String contact) {
        String token = HmacSigner.base64Url(contact) + "." + HmacSigner.hexHmac(secret, contact);
        return baseUrl + "/api/v1/notifications/unsubscribe?token=" + token;
    }

    /** Обрабатывает переход по ссылке отписки: проверяет подпись и пишет отзыв MARKETING-согласия. */
    @Transactional
    public void unsubscribe(String token, String ip) {
        String contact = verify(token);
        consents.recordMarketing(null, contact, false, ip, "notification_unsubscribe");
    }

    /** Отписан ли контакт (последняя MARKETING-запись — отзыв). */
    public boolean isOptedOut(String contact) {
        Boolean granted = jdbc.query(
                "select granted from consents where contact = ? and consent_type = 'MARKETING' "
                        + "order by created_at desc, id desc limit 1",
                rs -> rs.next() ? rs.getBoolean("granted") : null, contact);
        return Boolean.FALSE.equals(granted);
    }

    /** Подавлять ли отправку: только нетранзакционное уведомление отписавшемуся. */
    public boolean suppresses(boolean transactional, String contact) {
        return !transactional && isOptedOut(contact);
    }

    private String verify(String token) {
        int dot = token == null ? -1 : token.indexOf('.');
        if (dot <= 0) {
            throw new ValidationException("notification.unsubscribe.invalid_token");
        }
        String contact;
        try {
            contact = HmacSigner.fromBase64Url(token.substring(0, dot));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("notification.unsubscribe.invalid_token");
        }
        String signature = token.substring(dot + 1);
        if (!HmacSigner.matches(HmacSigner.hexHmac(secret, contact), signature)) {
            throw new ValidationException("notification.unsubscribe.invalid_token");
        }
        return contact;
    }
}
