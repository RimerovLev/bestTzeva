package com.tzevabest.common;

import com.tzevabest.common.web.log.LogMasker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** T-14.1: маскирование чувствительных данных в логах. */
class LogMaskerTest {

    @Test
    void masks_secrets_email_phone_card() {
        assertThat(LogMasker.mask("password=hunter2 next")).contains("password=***").doesNotContain("hunter2");
        assertThat(LogMasker.mask("token: abc123def")).contains("token=***").doesNotContain("abc123def");
        assertThat(LogMasker.mask("Authorization: Bearer aaa.bbb.ccc")).contains("Bearer ***");
        assertThat(LogMasker.mask("email dana@example.com")).contains("d***@example.com").doesNotContain("dana@");
        assertThat(LogMasker.mask("call +972501234567 now")).contains("***4567").doesNotContain("501234567");
        assertThat(LogMasker.mask("card 4111 1111 1111 1111")).contains("****").doesNotContain("4111 1111");
    }

    @Test
    void leaves_plain_text_untouched() {
        assertThat(LogMasker.mask("order 42 completed")).isEqualTo("order 42 completed");
    }
}
