package com.tzevabest.service.event;

import com.tzevabest.notification.dto.NotificationTemplate;
import com.tzevabest.notification.dto.NewRequestAdminModel;
import com.tzevabest.notification.dto.ServiceRequestReceivedModel;
import com.tzevabest.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Уведомления по созданной заявке на покраску: подтверждение клиенту и оповещение менеджеров. Ставит
 * их в очередь (transactional outbox) ДО коммита — в той же транзакции, что и создание заявки, чтобы
 * уведомление не потерялось при падении между коммитом и постановкой (T-12.2).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ServiceRequestNotifier {

    private final NotificationService notifications;
    private final JdbcTemplate jdbc;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCreated(ServiceRequestCreatedEvent event) {
        if (event.customerEmail() != null && !event.customerEmail().isBlank()) {
            notifications.enqueue(NotificationTemplate.SERVICE_REQUEST_RECEIVED, event.customerEmail(),
                    event.locale(), new ServiceRequestReceivedModel(event.requestNumber()),
                    "service_request:" + event.requestId());
        }
        List<String> managerEmails = jdbc.queryForList(
                "select email from users where role in ('MANAGER','ADMIN') and email is not null "
                        + "and status = 'ACTIVE'", String.class);
        for (String email : managerEmails) {
            notifications.enqueue(NotificationTemplate.NEW_REQUEST_ADMIN, email, "he",
                    new NewRequestAdminModel(event.requestNumber(), "painting"),
                    "service_request_admin:" + event.requestId() + ":" + email);
        }
    }
}
