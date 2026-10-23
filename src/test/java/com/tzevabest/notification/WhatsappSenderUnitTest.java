package com.tzevabest.notification;

import com.tzevabest.notification.service.NonRetryableNotificationException;
import com.tzevabest.notification.service.whatsapp.MetaWhatsappSender;
import com.tzevabest.notification.service.whatsapp.WhatsappMessages;
import com.tzevabest.notification.service.whatsapp.WhatsappProperties;
import com.tzevabest.notification.service.whatsapp.WhatsappTemplateMessage;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** WhatsApp: сборка payload Graph API (Meta) и резолвинг шаблона/языка — без сети. */
class WhatsappSenderUnitTest {

    @Test
    void meta_payload_is_a_template_message_with_body_params() {
        WhatsappProperties props = new WhatsappProperties();
        props.setPhoneNumberId("100");
        props.setAccessToken("tok");
        MetaWhatsappSender sender = new MetaWhatsappSender(props, RestClient.create());

        Map<String, Object> body = sender.buildBody("+972500000000",
                new WhatsappTemplateMessage("otp_code", "he", List.of("111222")));

        assertThat(body.get("messaging_product")).isEqualTo("whatsapp");
        assertThat(body.get("to")).isEqualTo("+972500000000");
        assertThat(body.get("type")).isEqualTo("template");
        @SuppressWarnings("unchecked")
        Map<String, Object> template = (Map<String, Object>) body.get("template");
        assertThat(template.get("name")).isEqualTo("otp_code");
        assertThat(((Map<?, ?>) template.get("language")).get("code")).isEqualTo("he");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) template.get("components");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> params = (List<Map<String, Object>>) components.get(0).get("parameters");
        assertThat(params).hasSize(1);
        assertThat(params.get(0)).containsEntry("type", "text").containsEntry("text", "111222");
    }

    @Test
    void meta_payload_without_params_omits_components() {
        WhatsappProperties props = new WhatsappProperties();
        props.setPhoneNumberId("100");
        MetaWhatsappSender sender = new MetaWhatsappSender(props, RestClient.create());

        Map<String, Object> body = sender.buildBody("+972500000000",
                new WhatsappTemplateMessage("account_locked", "en_US", List.of()));
        @SuppressWarnings("unchecked")
        Map<String, Object> template = (Map<String, Object>) body.get("template");
        assertThat(template).doesNotContainKey("components");
    }

    @Test
    void resolver_maps_name_and_language() {
        WhatsappProperties props = new WhatsappProperties();
        props.getTemplates().put("otp_code", "wa_otp");
        WhatsappMessages messages = new WhatsappMessages(props);

        WhatsappTemplateMessage msg = messages.resolve("otp_code", "ru", List.of("1"));
        assertThat(msg.templateName()).isEqualTo("wa_otp");
        assertThat(msg.languageCode()).isEqualTo("ru");           // из дефолтной карты языков
        assertThat(messages.resolve("otp_code", "xx", List.of()).languageCode()).isEqualTo("en_US"); // фолбэк
    }

    @Test
    void resolver_rejects_unmapped_code_non_retryable() {
        assertThatThrownBy(() -> new WhatsappMessages(new WhatsappProperties()).resolve("nope", "he", List.of()))
                .isInstanceOf(NonRetryableNotificationException.class);
    }
}
