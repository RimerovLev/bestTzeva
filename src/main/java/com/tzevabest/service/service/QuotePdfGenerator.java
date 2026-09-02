package com.tzevabest.service.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Генерация PDF коммерческого предложения на иврите (T-11.3). Шрифт с поддержкой иврита встроен
 * (Noto Sans Hebrew, OFL) — без него иврит вышел бы пустым. Вёрстка RTL: строки выравниваются по
 * правому краю, ивритский текст разворачивается для чтения справа налево. Символы вне шрифта
 * заменяются, чтобы генерация не падала на произвольном вводе.
 */
@Component
public class QuotePdfGenerator {

    private static final String FONT_PATH = "fonts/NotoSansHebrew-Regular.ttf";
    private static final float MARGIN = 56f;
    private static final float TITLE_SIZE = 18f;
    private static final float TEXT_SIZE = 12f;
    private static final float LEADING = 20f;

    /** Данные для КП. */
    public record QuoteData(String requestNumber, String customerName, BigDecimal price,
                            String workComposition, Integer termDays, String comment) {
    }

    public byte[] generate(QuoteData data) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType0Font font;
            try (InputStream fontStream = new ClassPathResource(FONT_PATH).getInputStream()) {
                font = PDType0Font.load(doc, fontStream, true); // встраивание + сабсеттинг
            }

            float right = page.getMediaBox().getWidth() - MARGIN;
            float y = page.getMediaBox().getHeight() - MARGIN;

            List<Line> lines = new ArrayList<>();
            lines.add(new Line("הצעת מחיר — Tzeva Best", TITLE_SIZE));
            lines.add(new Line("מספר בקשה: " + data.requestNumber(), TEXT_SIZE));
            lines.add(new Line("לקוח: " + safe(data.customerName()), TEXT_SIZE));
            lines.add(new Line("היקף עבודות: " + safe(data.workComposition()), TEXT_SIZE));
            lines.add(new Line("מחיר: " + data.price().toPlainString() + " ILS", TEXT_SIZE));
            if (data.termDays() != null) {
                lines.add(new Line("משך ביצוע: " + data.termDays() + " ימים", TEXT_SIZE));
            }
            if (data.comment() != null && !data.comment().isBlank()) {
                lines.add(new Line("הערות: " + safe(data.comment()), TEXT_SIZE));
            }
            lines.add(new Line("המחיר סופי לאחר מדידה באתר.", TEXT_SIZE));

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (Line line : lines) {
                    String display = reverseForRtl(sanitize(line.text, font));
                    float width = font.getStringWidth(display) / 1000 * line.size;
                    cs.beginText();
                    cs.setFont(font, line.size);
                    cs.newLineAtOffset(right - width, y); // выравнивание по правому краю (RTL)
                    cs.showText(display);
                    cs.endText();
                    y -= LEADING;
                }
            }

            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Line(String text, float size) {
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** Оставляет только символы, для которых у шрифта есть глиф (иначе showText бросил бы исключение). */
    private static String sanitize(String text, PDType0Font font) {
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            try {
                font.getStringWidth(ch); // бросит, если глифа нет
                sb.append(ch);
            } catch (Exception noGlyph) {
                sb.append(' ');
            }
        });
        return sb.toString();
    }

    /** Разворачивает строку для RTL-чтения (иврит без сложного шейпинга — достаточно реверса). */
    private static String reverseForRtl(String s) {
        return new StringBuilder(s).reverse().toString();
    }
}
