package com.tzevabest.notification;

import com.tzevabest.notification.dto.NotificationTemplate;
import com.tzevabest.notification.dto.ProductRequestModel;
import com.tzevabest.notification.service.NotificationDispatcher;
import com.tzevabest.notification.service.NotificationService;
import com.tzevabest.notification.service.WhatsappSender;
import com.tzevabest.notification.service.whatsapp.WhatsappTemplateMessage;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WhatsApp-канал (Meta, шаблоны): параметры подстановок сохраняются в очереди и доезжают до отправки
 * как параметры approved-шаблона; код уведомления маппится на имя шаблона из конфигурации.
 */
@Import(NotificationWhatsappTest.CapturingConfig.class)
@TestPropertySource(properties = "app.notification.whatsapp.templates.product_request_received=pr_received")
class NotificationWhatsappTest extends IntegrationTest {

    @Autowired
    private NotificationService notifications;
    @Autowired
    private NotificationDispatcher dispatcher;
    @Autowired
    private CapturingWhatsapp whatsapp;

    @Test
    void enqueue_persists_template_params() {
        notifications.enqueue(NotificationTemplate.PRODUCT_REQUEST_RECEIVED, "a@x.test", "he",
                new ProductRequestModel("PR-1"), "PR-1");
        String params = jdbcTemplate.queryForObject(
                "select template_params::text from notifications_log where recipient = 'a@x.test'", String.class);
        assertThat(params).contains("PR-1"); // JSON-массив значений подстановок модели
    }

    @Test
    void whatsapp_row_is_sent_as_template_with_params() {
        // Строка очереди на канал WHATSAPP (код товара-заявки сопоставлен шаблону pr_received).
        jdbcTemplate.update("""
                insert into notifications_log (channel, template_code, recipient, locale, status, priority,
                        attempt_count, next_attempt_at, template_params)
                values ('WHATSAPP','product_request_received','+972500000000','he','QUEUED','NORMAL',0, now(),
                        ?::jsonb)""", "[\"PR-9\"]");

        int processed = dispatcher.processBatch();
        assertThat(processed).isEqualTo(1);

        assertThat(whatsapp.lastTo).isEqualTo("+972500000000");
        WhatsappTemplateMessage msg = whatsapp.last;
        assertThat(msg.templateName()).isEqualTo("pr_received"); // имя approved-шаблона из конфига
        assertThat(msg.languageCode()).isEqualTo("he");
        assertThat(msg.bodyParams()).containsExactly("PR-9");

        String status = jdbcTemplate.queryForObject(
                "select status from notifications_log where recipient = '+972500000000'", String.class);
        assertThat(status).isEqualTo("SENT");
    }

    static class CapturingWhatsapp implements WhatsappSender {
        volatile String lastTo;
        volatile WhatsappTemplateMessage last;

        @Override
        public String send(String to, WhatsappTemplateMessage message) {
            this.lastTo = to;
            this.last = message;
            return "wa-" + to;
        }
    }

    @TestConfiguration
    static class CapturingConfig {
        @Bean
        @Primary
        CapturingWhatsapp capturingWhatsapp() {
            return new CapturingWhatsapp();
        }
    }
}
