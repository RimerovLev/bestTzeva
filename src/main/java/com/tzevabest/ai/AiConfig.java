package com.tzevabest.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Включает конфиг-свойства OpenAI и Turnstile. RestClient сервисы строят сами (со своими таймаутами). */
@Configuration
@EnableConfigurationProperties({OpenAiProperties.class, TurnstileProperties.class})
public class AiConfig {
}
