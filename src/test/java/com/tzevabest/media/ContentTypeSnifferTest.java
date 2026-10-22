package com.tzevabest.media;

import com.tzevabest.media.service.ContentTypeSniffer;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** Аудит п.9: тип по сигнатуре; разметка (SVG/HTML/XML) и неизвестное — отклоняются. */
class ContentTypeSnifferTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Test
    void detects_real_png() {
        assertThat(ContentTypeSniffer.detect(PNG)).isEqualTo("image/png");
    }

    @Test
    void detects_jpeg_and_pdf_by_magic() {
        assertThat(ContentTypeSniffer.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0}))
                .isEqualTo("image/jpeg");
        assertThat(ContentTypeSniffer.detect("%PDF-1.7\n........".getBytes())).isEqualTo("application/pdf");
    }

    @Test
    void rejects_svg_html_and_garbage() {
        assertThat(ContentTypeSniffer.detect("<svg xmlns=\"...\"><script/></svg>".getBytes())).isNull();
        assertThat(ContentTypeSniffer.detect("<!DOCTYPE html><html></html>".getBytes())).isNull();
        assertThat(ContentTypeSniffer.detect("<?xml version=\"1.0\"?><svg/>".getBytes())).isNull();
        assertThat(ContentTypeSniffer.detect("not-an-image".getBytes())).isNull();
        assertThat(ContentTypeSniffer.detect(new byte[]{1, 2, 3})).isNull();
        assertThat(ContentTypeSniffer.detect(null)).isNull();
    }
}
