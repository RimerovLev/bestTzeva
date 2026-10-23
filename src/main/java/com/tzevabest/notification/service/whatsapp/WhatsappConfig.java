package com.tzevabest.notification.service.whatsapp;

import com.tzevabest.notification.service.LoggingWhatsappSender;
import com.tzevabest.notification.service.WhatsappSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Проводка канала WhatsApp. Ровно один {@link WhatsappSender} активен: реальный Meta-sender при
 * {@code app.notification.whatsapp.enabled=true}, иначе заглушка (по умолчанию выключено).
 */
@Configuration
@EnableConfigurationProperties(WhatsappProperties.class)
public class WhatsappConfig {

    @Bean
    WhatsappMessages whatsappMessages(WhatsappProperties props) {
        return new WhatsappMessages(props);
    }

    @Bean
    @ConditionalOnProperty(name = "app.notification.whatsapp.enabled", havingValue = "true")
    WhatsappSender metaWhatsappSender(WhatsappProperties props) {
        return new MetaWhatsappSender(props, RestClient.create());
    }

    @Bean
    @ConditionalOnProperty(name = "app.notification.whatsapp.enabled", havingValue = "false", matchIfMissing = true)
    WhatsappSender loggingWhatsappSender() {
        return new LoggingWhatsappSender();
    }
}
