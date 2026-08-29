package com.tzevabest.notification;

import com.tzevabest.notification.service.SmsSegmenter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** T-12.3: подсчёт SMS-сегментов. Иврит — UCS-2 (70/67), латиница — GSM-7 (160/153). */
class SmsSegmenterTest {

    @Test
    void latin_is_gsm7_with_160_per_segment() {
        assertThat(SmsSegmenter.segment("Hello").encoding()).isEqualTo(SmsSegmenter.Encoding.GSM7);
        assertThat(SmsSegmenter.segment("a".repeat(160)).segments()).isEqualTo(1);
        assertThat(SmsSegmenter.segment("a".repeat(161)).segments()).isEqualTo(2); // ceil(161/153)
        assertThat(SmsSegmenter.segment("a".repeat(306)).segments()).isEqualTo(2); // 2*153
        assertThat(SmsSegmenter.segment("a".repeat(307)).segments()).isEqualTo(3);
    }

    @Test
    void hebrew_is_ucs2_with_70_per_segment() {
        SmsSegmenter.Segmentation one = SmsSegmenter.segment("שלום");
        assertThat(one.encoding()).isEqualTo(SmsSegmenter.Encoding.UCS2);
        assertThat(one.segments()).isEqualTo(1);

        assertThat(SmsSegmenter.segment("א".repeat(70)).segments()).isEqualTo(1);
        assertThat(SmsSegmenter.segment("א".repeat(71)).segments()).isEqualTo(2); // ceil(71/67)
        assertThat(SmsSegmenter.segment("א".repeat(134)).segments()).isEqualTo(2); // 2*67
        assertThat(SmsSegmenter.segment("א".repeat(135)).segments()).isEqualTo(3);
    }

    @Test
    void empty_is_single_segment() {
        assertThat(SmsSegmenter.segment("").segments()).isEqualTo(1);
    }
}
