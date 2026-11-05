package com.tzevabest.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.tzevabest.ai.dto.RoomRenderResult;
import com.tzevabest.common.web.error.BusinessException;
import com.tzevabest.common.web.error.ValidationException;
import com.tzevabest.media.service.ContentTypeSniffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * «Примерка цвета»: отправляет фото комнаты в OpenAI Images Edit (gpt-image-1) с промптом «перекрась
 * только стены в цвет X, остальное сохрани». Возвращает фотореалистичный результат. Витрина делает
 * один вызов на комнату, а смену цвета — локально (по маске), поэтому расход на OpenAI минимален.
 */
@Service
public class ColorStudioService {

    private static final Logger log = LoggerFactory.getLogger(ColorStudioService.class);
    private static final long MAX_BYTES = 12L * 1024 * 1024;
    private static final Pattern HEX = Pattern.compile("^#?[0-9A-Fa-f]{6}$");
    private static final Pattern NAME_ALLOWED = Pattern.compile("[^\\p{L}0-9 .\\-]");

    private final OpenAiProperties props;
    private final RestClient client;

    public ColorStudioService(OpenAiProperties props) {
        this.props = props;
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public RoomRenderResult renderWalls(MultipartFile file, String color, String name) {
        if (!props.isConfigured()) {
            throw new BusinessException("ai.not_configured", HttpStatus.SERVICE_UNAVAILABLE);
        }
        String hex = normalizeHex(color);
        String safeName = sanitizeName(name);
        byte[] data = read(file);
        // Тип — по сигнатуре содержимого, не со слов клиента (в OpenAI шлём только изображение).
        String type = ContentTypeSniffer.detect(data);
        if (type == null || !type.startsWith("image/")) {
            throw new ValidationException("ai.invalid_image");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", props.getModel());
        body.add("prompt", buildPrompt(hex, safeName));
        body.add("size", sizeFor(data));
        body.add("quality", props.getQuality());
        body.add("n", "1");
        body.add("image", imagePart(data, type));

        // OpenAI проверяет mimetype части image по заголовку Content-Type (не по имени файла).
        // Без явного типа Spring шлёт application/octet-stream → 400 unsupported_file_mimetype.

        try {
            JsonNode res = client.post()
                    .uri(props.getBaseUrl() + "/images/edits")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode b64 = res == null ? null : res.path("data").path(0).path("b64_json");
            if (b64 == null || b64.isMissingNode() || b64.asText().isBlank()) {
                throw new BusinessException("ai.render_failed", HttpStatus.BAD_GATEWAY);
            }
            return new RoomRenderResult("data:image/png;base64," + b64.asText());
        } catch (RestClientResponseException e) {
            // В теле OpenAI — причина отказа (без секретов), нужна для диагностики.
            log.warn("OpenAI image edit failed: HTTP {} — {}", e.getStatusCode().value(),
                    e.getResponseBodyAsString());
            throw new BusinessException("ai.render_failed", HttpStatus.BAD_GATEWAY);
        } catch (RestClientException e) {
            log.warn("OpenAI image edit error: {}", e.getClass().getSimpleName());
            throw new BusinessException("ai.render_failed", HttpStatus.BAD_GATEWAY);
        }
    }

    private String buildPrompt(String hex, String name) {
        String color = hex + (name.isBlank() ? "" : " (" + name + ")");
        return "Repaint only the walls of this interior room with the matte paint color " + color
                + ". Keep every piece of furniture, the floor, the ceiling, windows, doors, decorations, "
                + "plants, people, the lighting, shadows and the exact camera framing completely unchanged. "
                + "The walls must look like a real painted matte surface in that exact color, with natural "
                + "light and soft shadows preserved. Photorealistic, high detail, same composition.";
    }

    /** Размер вывода под ориентацию входа (gpt-image-1 поддерживает 1024², 1536×1024, 1024×1536). */
    private String sizeFor(byte[] data) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img != null && img.getWidth() > 0 && img.getHeight() > 0) {
                int w = img.getWidth();
                int h = img.getHeight();
                if (w >= h * 1.2) {
                    return "1536x1024";
                }
                if (h >= w * 1.2) {
                    return "1024x1536";
                }
            }
        } catch (IOException ignored) {
            // неизвестный формат для ImageIO (напр. webp без плагина) — отдадим квадрат
        }
        return "1024x1024";
    }

    private HttpEntity<Resource> imagePart(byte[] data, String type) {
        String ext = switch (type) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        Resource res = new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return "room." + ext;
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(type));
        return new HttpEntity<>(res, headers);
    }

    private static String normalizeHex(String color) {
        if (color == null || !HEX.matcher(color.trim()).matches()) {
            throw new ValidationException("ai.invalid_color");
        }
        String c = color.trim();
        return c.startsWith("#") ? c : "#" + c;
    }

    private static String sanitizeName(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = NAME_ALLOWED.matcher(name).replaceAll("").trim();
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("ai.empty_image");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ValidationException("ai.image_too_large");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("ai.unreadable");
        }
    }
}
