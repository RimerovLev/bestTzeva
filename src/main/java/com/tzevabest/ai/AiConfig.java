package com.tzevabest.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Включает конфиг-свойства OpenAI. Сам RestClient сервис строит с таймаутом (генерация — долгая). */
@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class AiConfig {
}
