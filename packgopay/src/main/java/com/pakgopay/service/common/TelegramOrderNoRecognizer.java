package com.pakgopay.service.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TelegramOrderNoRecognizer {

    private static final Pattern ORDER_NO_STRICT_PATTERN =
            Pattern.compile("(?<![A-Z0-9])(?:COLL|PAY|SE)\\d{8,30}(?![A-Z0-9])");
    // tolerate OCR mistakes: prefix C0LL/S3 and ambiguous chars in numeric body
    private static final Pattern ORDER_NO_FUZZY_PATTERN =
            Pattern.compile("(?<![A-Z0-9])(?:C[O0]LL|PAY|S[E3])[0-9OILSBZGQ]{8,30}(?![A-Z0-9])");

    public String recognizeSystemOrderNo(byte[] imageBytes, String fallbackText) {
        String fromText = extractSystemOrderNo(fallbackText);
        if (StringUtils.hasText(fromText)) {
            return fromText;
        }
        String ocrText = recognizeTextByTesseract(imageBytes);
        String orderNo = extractSystemOrderNo(ocrText);
        if (!StringUtils.hasText(orderNo)) {
            String preview = ocrText == null ? "null" : ocrText.replaceAll("\\s+", " ").trim();
            if (preview.length() > 200) {
                preview = preview.substring(0, 200) + "...";
            }
            log.info("telegram ocr no order matched, ocrPreview={}", preview);
        }
        return orderNo;
    }

    public String extractSystemOrderNo(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String upper = text.toUpperCase();
        String compact = upper.replaceAll("[^A-Z0-9]", "");

        // Priority: strict from original text -> strict from compact -> fuzzy.
        String strict = findFirstByPattern(upper, ORDER_NO_STRICT_PATTERN, false);
        if (StringUtils.hasText(strict)) {
            return strict;
        }
        strict = findFirstByPattern(compact, ORDER_NO_STRICT_PATTERN, false);
        if (StringUtils.hasText(strict)) {
            return strict;
        }
        String fuzzy = findBestFuzzy(upper);
        if (StringUtils.hasText(fuzzy)) {
            return fuzzy;
        }
        return findBestFuzzy(compact);
    }

    private String findFirstByPattern(String text, Pattern pattern, boolean normalize) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group();
        if (!normalize) {
            return value;
        }
        return normalizeOrderNo(value);
    }

    private String findBestFuzzy(String text) {
        Matcher matcher = ORDER_NO_FUZZY_PATTERN.matcher(text);
        Set<String> candidates = new LinkedHashSet<>();
        while (matcher.find()) {
            String normalized = normalizeOrderNo(matcher.group());
            if (isStrictOrderNo(normalized)) {
                candidates.add(normalized);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        List<String> list = new ArrayList<>(candidates);
        list.sort((a, b) -> {
            int la = a.length();
            int lb = b.length();
            if (la != lb) {
                return Integer.compare(lb, la);
            }
            return a.compareTo(b);
        });
        return list.get(0);
    }

    private String normalizeOrderNo(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        String upper = raw.toUpperCase();
        String prefix;
        String body;
        if (upper.startsWith("C0LL")) {
            prefix = "COLL";
            body = upper.substring(4);
        } else if (upper.startsWith("COLL")) {
            prefix = "COLL";
            body = upper.substring(4);
        } else if (upper.startsWith("PAY")) {
            prefix = "PAY";
            body = upper.substring(3);
        } else if (upper.startsWith("S3")) {
            prefix = "SE";
            body = upper.substring(2);
        } else if (upper.startsWith("SE")) {
            prefix = "SE";
            body = upper.substring(2);
        } else {
            return upper;
        }
        return prefix + normalizeNumericBody(body);
    }

    private String normalizeNumericBody(String body) {
        StringBuilder sb = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            switch (c) {
                case 'O':
                case 'Q':
                    sb.append('0');
                    break;
                case 'I':
                case 'L':
                    sb.append('1');
                    break;
                case 'S':
                    sb.append('5');
                    break;
                case 'B':
                    sb.append('8');
                    break;
                case 'Z':
                    sb.append('2');
                    break;
                case 'G':
                    sb.append('6');
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    private boolean isStrictOrderNo(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return ORDER_NO_STRICT_PATTERN.matcher(value).find();
    }

    private String recognizeTextByTesseract(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        File imageFile = null;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                log.warn("telegram image ocr skipped: not an image");
                return null;
            }
            imageFile = File.createTempFile("tg-order-", ".png");
            ImageIO.write(image, "png", imageFile);

            ProcessBuilder pb = new ProcessBuilder(
                    "tesseract",
                    imageFile.getAbsolutePath(),
                    "stdout",
                    "-l", "eng",
                    "--psm", "6"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            byte[] out = process.getInputStream().readAllBytes();
            int code = process.waitFor();
            if (code != 0) {
                log.warn("telegram image ocr failed, exitCode={}, output={}", code, new String(out, StandardCharsets.UTF_8));
                return null;
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("telegram image ocr failed: {}", e.getMessage());
            return null;
        } finally {
            if (imageFile != null) {
                try {
                    Files.deleteIfExists(imageFile.toPath());
                } catch (Exception ignored) {
                }
            }
        }
    }
}
