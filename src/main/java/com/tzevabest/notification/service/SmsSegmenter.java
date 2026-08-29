package com.tzevabest.notification.service;

/**
 * Подсчёт SMS-сегментов (T-12.3). Латиница укладывается в GSM-7 (160 символов в одном сегменте, 153
 * в составном). Иврит и прочие не-GSM символы кодируются UCS-2 — сегмент всего 70 символов (67 в
 * составном), поэтому ивритский текст «дороже» и рвётся раньше.
 */
public final class SmsSegmenter {

    // Базовый алфавит GSM 03.38 (без расширения) — то, что помещается в 7-битную кодировку.
    private static final String GSM7 =
            "@£$¥èéùìòÇ\nØø\rÅå"
                    + "Δ_ΦΓΛΩΠΨΣΘΞÆæßÉ"
                    + " !\"#¤%&'()*+,-./0123456789:;<=>?"
                    + "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§"
                    + "¿abcdefghijklmnopqrstuvwxyzäöñüà";

    private SmsSegmenter() {
    }

    public enum Encoding { GSM7, UCS2 }

    /** Результат: кодировка, число сегментов, длина в символах. */
    public record Segmentation(Encoding encoding, int segments, int length) {
        public boolean isSplit() {
            return segments > 1;
        }
    }

    public static Segmentation segment(String message) {
        String text = message == null ? "" : message;
        int length = text.codePointCount(0, text.length());
        Encoding encoding = isGsm7(text) ? Encoding.GSM7 : Encoding.UCS2;

        int single = encoding == Encoding.GSM7 ? 160 : 70;
        int concat = encoding == Encoding.GSM7 ? 153 : 67;
        int segments = (length <= single) ? 1 : (int) Math.ceil((double) length / concat);
        return new Segmentation(encoding, segments, length);
    }

    private static boolean isGsm7(String text) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp > 0xFFFF || GSM7.indexOf(cp) < 0) {
                return false;
            }
        }
        return true;
    }
}
