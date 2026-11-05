package com.tzevabest.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Настройки OpenAI для «примерки цвета» (image-edit). Ключ — из окружения, пусто = фича выключена. */
@ConfigurationProperties("app.ai.openai")
public class OpenAiProperties {

    /** Секретный ключ API (Bearer). Пусто → рендер недоступен (503). */
    private String apiKey = "";
    private String baseUrl = "https://api.openai.com/v1";
    private String model = "gpt-image-2.5-flare";
    /** Качество генерации: low | medium | high (цена/скорость). */
    private String quality = "medium";
    private int timeoutSeconds = 120;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
