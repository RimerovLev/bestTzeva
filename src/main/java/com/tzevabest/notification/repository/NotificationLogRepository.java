package com.tzevabest.notification.repository;

import com.tzevabest.notification.domain.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** Журнал уведомлений. */
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
}
