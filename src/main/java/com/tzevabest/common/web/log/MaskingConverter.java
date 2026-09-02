package com.tzevabest.common.web.log;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Logback-конвертер: маскирует чувствительные данные в сообщении лога. Регистрируется как %mask. */
public class MaskingConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return LogMasker.mask(super.convert(event));
    }
}
