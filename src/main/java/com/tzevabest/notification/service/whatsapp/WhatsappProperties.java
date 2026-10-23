package com.tzevabest.notification.service.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Настройки канала WhatsApp (Meta WhatsApp Business Cloud API). За флагом {@code enabled}: пока
 * выключен — работает заглушка. Сообщения, инициированные бизнесом (OTP, статусы заявок), WhatsApp
 * доставляет только по ОДОБРЕННЫМ шаблонам, поэтому на каждый код уведомления задаётся имя
 * approved-шаблона, а язык маппится из локали пользователя.
 */
@ConfigurationProperties("app.notification.whatsapp")
public class WhatsappProperties {

    /** Канал включён (в prod — реальный Meta-sender, иначе заглушка). */
    private boolean enabled = false;
    /** ID номера-отправителя из WhatsApp Business (Graph API: /{phone-number-id}/messages). */
    private String phoneNumberId;
    /** Постоянный/системный токен доступа (Bearer). */
    private String accessToken;
    /** Версия Graph API. */
    private String apiVersion = "v21.0";
    /** База Graph API (для тестов/проксирования можно переопределить). */
    private String baseUrl = "https://graph.facebook.com";
    /** Код уведомления ({@code NotificationTemplate.code()}) → имя approved WhatsApp-шаблона. */
    private Map<String, String> templates = new HashMap<>();
    /** Локаль приложения (he/ru/en) → языковой код шаблона WhatsApp. */
    private Map<String, String> languages = new HashMap<>(Map.of("he", "he", "ru", "ru", "en", "en_US"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Map<String, String> getTemplates() {
        return templates;
    }

    public void setTemplates(Map<String, String> templates) {
        this.templates = templates;
    }

    public Map<String, String> getLanguages() {
        return languages;
    }

    public void setLanguages(Map<String, String> languages) {
        this.languages = languages;
    }
}
