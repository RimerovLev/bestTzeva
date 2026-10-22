package com.tzevabest.media.service;

import java.util.Arrays;

/**
 * Определение реального типа файла по сигнатуре (magic bytes), а не по заявленному клиентом
 * Content-Type (аудит п.9). Нужна, чтобы в публичный бакет не попали SVG/HTML/XHTML, которые
 * раздаются inline и дают stored XSS на домене медиа. Возвращается канонический MIME ровно для
 * набора безопасных бинарных типов; для всего остального (в т.ч. любого текста/разметки) — {@code null}.
 */
public final class ContentTypeSniffer {

    private ContentTypeSniffer() {
    }

    /** Канонический MIME по сигнатуре содержимого, либо {@code null} если тип не распознан/не разрешён. */
    public static String detect(byte[] data) {
        if (data == null || data.length < 12) {
            return null;
        }
        if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(data, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (match(data, 0, "GIF87a") || match(data, 0, "GIF89a")) {
            return "image/gif";
        }
        if (match(data, 0, "RIFF") && match(data, 8, "WEBP")) {
            return "image/webp";
        }
        if (match(data, 4, "ftyp")) {
            return "video/mp4";
        }
        if (startsWith(data, 0x1A, 0x45, 0xDF, 0xA3)) {
            return "video/webm";
        }
        if (match(data, 0, "%PDF-")) {
            return "application/pdf";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int... bytes) {
        if (data.length < bytes.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if ((data[i] & 0xFF) != bytes[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean match(byte[] data, int offset, String ascii) {
        byte[] expected = ascii.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (data.length < offset + expected.length) {
            return false;
        }
        return Arrays.equals(data, offset, offset + expected.length, expected, 0, expected.length);
    }
}
