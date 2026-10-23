package com.tzevabest.notification;

import com.tzevabest.notification.domain.NotificationChannel;
import com.tzevabest.notification.dto.NotificationTemplate;
import com.tzevabest.notification.dto.AccountAlertModel;
import com.tzevabest.notification.dto.LinkModel;
import com.tzevabest.notification.dto.LowStockAlertModel;
import com.tzevabest.notification.dto.MeasurementReminderModel;
import com.tzevabest.notification.dto.NewRequestAdminModel;
import com.tzevabest.notification.dto.NotificationModel;
import com.tzevabest.notification.dto.OtpModel;
import com.tzevabest.notification.dto.ProductRequestModel;
import com.tzevabest.notification.dto.RenderedMessage;
import com.tzevabest.notification.dto.ServiceRequestQuotedModel;
import com.tzevabest.notification.dto.ServiceRequestReceivedModel;
import com.tzevabest.notification.service.NotificationService;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-12.1: каждый шаблон рендерится на трёх локалях без незаполненных плейсхолдеров; email на иврите —
 * с dir=rtl; журнал уведомлений пишется.
 */
class NotificationRenderTest extends IntegrationTest {

    @Autowired
    private NotificationService notifications;

    private static final Pattern UNFILLED = Pattern.compile("\\{\\d");

    @Test
    void every_template_renders_on_three_locales_without_placeholders() {
        for (NotificationTemplate template : NotificationTemplate.values()) {
            for (String locale : new String[]{"he", "ru", "en"}) {
                RenderedMessage r = notifications.render(template, locale, sampleModel(template));

                assertThat(r.body())
                        .as("body of %s [%s]", template, locale)
                        .isNotBlank();
                assertThat(UNFILLED.matcher(r.body()).find())
                        .as("unfilled placeholder in body of %s [%s]: %s", template, locale, r.body())
                        .isFalse();

                if (template.channel() == NotificationChannel.EMAIL) {
                    assertThat(r.subject()).as("subject of %s [%s]", template, locale).isNotBlank();
                    assertThat(UNFILLED.matcher(r.subject()).find())
                            .as("unfilled placeholder in subject of %s [%s]", template, locale).isFalse();
                    // RTL для иврита, LTR для остальных
                    assertThat(r.body()).contains("dir=\"" + ("he".equals(locale) ? "rtl" : "ltr") + "\"");
                }
            }
        }
    }

    @Test
    void send_writes_notifications_log_snapshot() {
        Long before = jdbcTemplate.queryForObject("select count(*) from notifications_log", Long.class);
        notifications.send(NotificationTemplate.OTP_CODE, "+972501234567", "he", new OtpModel("123456"));

        Long after = jdbcTemplate.queryForObject("select count(*) from notifications_log", Long.class);
        assertThat(after).isEqualTo(before + 1);
        var row = jdbcTemplate.queryForMap(
                "select channel, template_code, recipient, status, attempt_count, body_snapshot "
                        + "from notifications_log order by id desc limit 1");
        assertThat(row.get("channel")).isEqualTo("SMS");
        assertThat(row.get("template_code")).isEqualTo("otp_code");
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(row.get("attempt_count")).isEqualTo(1);
        assertThat((String) row.get("body_snapshot")).contains("123456");
    }

    @Test
    void informational_email_carries_unsubscribe_link_transactional_does_not() {
        String recipient = "reader@example.com";
        // R-61: информационное (нетранзакционное) письмо несёт ссылку отписки с токеном
        RenderedMessage info = notifications.render(NotificationTemplate.MARKETING_NEWS, "en",
                new com.tzevabest.notification.dto.MarketingNewsModel(), recipient);
        assertThat(info.body()).contains("/unsubscribe?token="); // страница витрины (аудит п.7)

        // транзакционное — не несёт (блок скрыт)
        RenderedMessage txn = notifications.render(NotificationTemplate.PRODUCT_REQUEST_RECEIVED, "en",
                new ProductRequestModel("PR-260101-0001"), recipient);
        assertThat(txn.body()).doesNotContain("unsubscribe");
    }

    @Test
    void email_body_is_split_into_paragraphs() {
        // R-70: тело из двух абзацев (разделены пустой строкой) выводится двумя <p>, а не одним.
        RenderedMessage r = notifications.render(NotificationTemplate.MARKETING_NEWS, "en",
                new com.tzevabest.notification.dto.MarketingNewsModel(), "reader@example.com");
        int paragraphs = r.body().split("margin:0 0 12px 0;", -1).length - 1;
        assertThat(paragraphs).isEqualTo(2);
    }

    private static NotificationModel sampleModel(NotificationTemplate template) {
        return switch (template) {
            case PRODUCT_REQUEST_RECEIVED, PRODUCT_REQUEST_CONTACTED, PRODUCT_REQUEST_RESERVED,
                 PRODUCT_REQUEST_EXPIRED -> new ProductRequestModel("PR-260101-0001");
            case SERVICE_REQUEST_RECEIVED -> new ServiceRequestReceivedModel("SR-260101-0001");
            case SERVICE_REQUEST_QUOTED -> new ServiceRequestQuotedModel("SR-260101-0001", "1500 ILS");
            case MEASUREMENT_REMINDER -> new MeasurementReminderModel("SR-260101-0001", "2026-01-05 10:00");
            case PASSWORD_RESET, EMAIL_VERIFICATION -> new LinkModel("https://tzevabest.co.il/x?token=abc");
            case OTP_CODE -> new OtpModel("123456");
            case ACCOUNT_LOCKED, TOKEN_REPLAY -> new AccountAlertModel();
            case LOW_STOCK_ALERT -> new LowStockAlertModel("SKU-1", "WH-1", 2, 10);
            case NEW_REQUEST_ADMIN -> new NewRequestAdminModel("PR-260101-0001", "product");
            case MARKETING_NEWS -> new com.tzevabest.notification.dto.MarketingNewsModel();
        };
    }
}
