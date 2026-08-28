package com.tzevabest.request.event;

import com.tzevabest.notification.service.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Уведомления по созданной заявке: подтверждение клиенту (email/SMS) и оповещение менеджеров.
 * Слушает после коммита (заявка уже в БД). Ошибки уведомления не валят создание заявки.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductRequestNotifier {

    private final NotificationSender notifications;
    private final MessageSource messages;
    private final JdbcTemplate jdbc;

    @TransactionalEventListener
    public void onCreated(ProductRequestCreatedEvent event) {
        try {
            // Клиентское подтверждение на языке заявки (R-53), а не всегда на иврите/русском.
            Locale locale = Locale.forLanguageTag(event.locale() == null ? "he" : event.locale());
            Object[] args = {event.requestNumber()};
            String subject = messages.getMessage("notification.product_request.created.subject", args, locale);
            String body = messages.getMessage("notification.product_request.created.body", args, locale);
            if (event.customerEmail() != null && !event.customerEmail().isBlank()) {
                notifications.sendEmail(event.customerEmail(), subject, body);
            } else {
                notifications.sendSms(event.customerPhone(), body);
            }
            List<String> managerEmails = jdbc.queryForList(
                    "select email from users where role in ('MANAGER','ADMIN') and email is not null "
                            + "and status = 'ACTIVE'", String.class);
            String managerBody = "Новая заявка на товар: " + event.requestNumber();
            managerEmails.forEach(email -> notifications.sendEmail(email, "Новая заявка на товар", managerBody));
        } catch (RuntimeException e) {
            log.warn("Failed to send product-request notifications for {}", event.requestNumber(), e);
        }
    }
}
