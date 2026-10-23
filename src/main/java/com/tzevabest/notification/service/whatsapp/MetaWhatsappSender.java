package com.tzevabest.notification.service.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.tzevabest.notification.service.NonRetryableNotificationException;
import com.tzevabest.notification.service.WhatsappSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Отправка через Meta WhatsApp Business Cloud API: {@code POST {base}/{version}/{phone-number-id}/messages}
 * с Bearer-токеном, тип сообщения — {@code template}. Параметры тела шаблона ({{1}}, {{2}}, …)
 * передаются как компоненты body. 4xx (неверный номер/шаблон) — не ретраится; 429/5xx/сеть — ретраится.
 */
@Slf4j
public class MetaWhatsappSender implements WhatsappSender {

    private final WhatsappProperties props;
    private final RestClient restClient;
    private final String messagesUrl;

    public MetaWhatsappSender(WhatsappProperties props, RestClient restClient) {
        this.props = props;
        this.restClient = restClient;
        this.messagesUrl = props.getBaseUrl() + "/" + props.getApiVersion()
                + "/" + props.getPhoneNumberId() + "/messages";
    }

    @Override
    public String send(String to, WhatsappTemplateMessage message) {
        try {
            JsonNode res = restClient.post()
                    .uri(messagesUrl)
                    .header("Authorization", "Bearer " + props.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildBody(to, message))
                    .retrieve()
                    .body(JsonNode.class);
            return res == null ? null : res.path("messages").path(0).path("id").asText(null);
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 429 || code >= 500) {
                throw new RuntimeException("WhatsApp send failed: HTTP " + code, e); // ретраится
            }
            throw new NonRetryableNotificationException(
                    "WhatsApp send rejected: HTTP " + code + " " + e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            throw new RuntimeException("WhatsApp network error", e); // ретраится
        }
    }

    /** Тело запроса Graph API (public — чтобы юнит-тест проверял структуру без сети). */
    public Map<String, Object> buildBody(String to, WhatsappTemplateMessage message) {
        Map<String, Object> language = new LinkedHashMap<>();
        language.put("code", message.languageCode());

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", message.templateName());
        template.put("language", language);

        List<String> params = message.bodyParams();
        if (params != null && !params.isEmpty()) {
            List<Map<String, Object>> parameters = new ArrayList<>();
            for (String p : params) {
                Map<String, Object> param = new LinkedHashMap<>();
                param.put("type", "text");
                param.put("text", p == null ? "" : p);
                parameters.add(param);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type", "body");
            body.put("parameters", parameters);
            template.put("components", List.of(body));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "template");
        payload.put("template", template);
        return payload;
    }
}
