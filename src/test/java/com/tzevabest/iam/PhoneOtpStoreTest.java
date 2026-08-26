package com.tzevabest.iam;

import com.tzevabest.iam.service.PhoneOtpStore;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** OTP: сверка, лимит попыток (атомарный счётчик), cooldown на повторную отправку. */
class PhoneOtpStoreTest extends IntegrationTest {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX = 5;

    @Autowired
    private PhoneOtpStore otp;

    @Test
    void verifies_correct_and_consumes() {
        otp.issue(1L, "123456", TTL, COOLDOWN);
        assertThat(otp.verify(1L, "000000", MAX)).isZero();     // неверно
        assertThat(otp.verify(1L, "123456", MAX)).isEqualTo(1); // верно
        assertThat(otp.verify(1L, "123456", MAX)).isEqualTo(-2); // уже израсходован
    }

    @Test
    void blocks_after_five_attempts() {
        otp.issue(2L, "123456", TTL, COOLDOWN);
        for (int i = 0; i < 5; i++) {
            assertThat(otp.verify(2L, "000000", MAX)).isZero();
        }
        // 6-я попытка блокируется даже с верным кодом
        assertThat(otp.verify(2L, "123456", MAX)).isEqualTo(-1);
    }

    @Test
    void cooldown_blocks_immediate_resend() {
        assertThat(otp.canSend(3L)).isTrue();
        otp.issue(3L, "123456", TTL, COOLDOWN);
        assertThat(otp.canSend(3L)).isFalse();
    }
}
