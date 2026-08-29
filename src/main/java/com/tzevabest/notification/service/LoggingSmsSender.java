package com.tzevabest.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Заглушка SMS до подключения провайдера (T-12.3). Логирует число сегментов ивритского текста. */
@Slf4j
@Component
public class LoggingSmsSender implements SmsSender {
    @Override
    public String send(String to, String message) {
        SmsSegmenter.Segmentation seg = SmsSegmenter.segment(message);
        if (seg.isSplit()) {
            log.warn("[SMS stub] to={} split into {} segments ({} chars, {})",
                    to, seg.segments(), seg.length(), seg.encoding());
        }
        String id = "log-" + UUID.randomUUID();
        log.info("[SMS stub] to={} segments={} id={}", to, seg.segments(), id);
        return id;
    }
}
