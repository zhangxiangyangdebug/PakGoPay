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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TelegramOrderNoRecognizer {

    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("(?<![A-Z0-9])(?:COLL|PAY|SE)[A-Z0-9]{6,}(?![A-Z0-9])");

    public String recognizeSystemOrderNo(byte[] imageBytes, String fallbackText) {
        String fromText = extractSystemOrderNo(fallbackText);
        if (StringUtils.hasText(fromText)) {
            return fromText;
        }
        String ocrText = recognizeTextByTesseract(imageBytes);
        return extractSystemOrderNo(ocrText);
    }

    public String extractSystemOrderNo(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String upper = text.toUpperCase();
        Matcher matcher = ORDER_NO_PATTERN.matcher(upper);
        if (matcher.find()) {
            return matcher.group();
        }
        String compact = upper.replaceAll("[^A-Z0-9]", "");
        matcher = ORDER_NO_PATTERN.matcher(compact);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
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

