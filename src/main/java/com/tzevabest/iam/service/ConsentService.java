package com.tzevabest.iam.service;

import com.tzevabest.iam.domain.Consent;
import com.tzevabest.iam.domain.ConsentType;
import com.tzevabest.iam.repository.ConsentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Запись согласий (append-only, израильский закон о защите приватности). Каждое согласие фиксирует
 * версию редакции текста ({@code app.privacy.policy-version}) — чтобы позже можно было ответить, на
 * какую именно редакцию согласился пользователь (T-14.2, R-39). Вызывается при регистрации, оформлении
 * заказа (T-7.1) и отправке заявки (T-11.1).
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRepository consents;

    @Value("${app.privacy.policy-version}")
    private String policyVersion;

    /**
     * Записать согласие на обработку данных (DATA_PROCESSING). Удобная обёртка для других модулей —
     * чтобы не тянуть iam-enum {@link ConsentType} через границу модуля.
     */
    @Transactional
    public void recordDataProcessing(Long userId, String contact, String ip, String source) {
        record(userId, contact, ConsentType.DATA_PROCESSING, true, ip, source);
    }

    /**
     * Записать согласие/отзыв MARKETING (рассылки). Обёртка для других модулей — отписка от
     * уведомлений (T-12.3) пишет отзыв, не тягая iam-enum {@link ConsentType} через границу модуля.
     */
    @Transactional
    public void recordMarketing(Long userId, String contact, boolean granted, String ip, String source) {
        record(userId, contact, ConsentType.MARKETING, granted, ip, source);
    }

    @Transactional
    public void record(Long userId, String contact, ConsentType type, boolean granted, String ip, String source) {
        Consent consent = new Consent();
        consent.setUserId(userId);
        consent.setContact(contact);
        consent.setConsentType(type);
        consent.setGranted(granted);
        consent.setIp(ip);
        consent.setSource(source);
        consent.setPolicyVersion(policyVersion);
        consents.save(consent);
    }
}
