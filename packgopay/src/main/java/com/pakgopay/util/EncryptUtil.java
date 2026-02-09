package com.pakgopay.util;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Encryption utilities (AES/GCM).
 */
public class EncryptUtil {

    private static final String AES_ALGO = "AES";
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final String HMAC_SHA1 = "HmacSHA1";
    private static final String KV_SEPARATOR = "=";
    private static final String PARAM_SEPARATOR = "&";
    private static final int GCM_TAG_LENGTH_BITS = 128;

    /**
     * Encrypt plaintext with AES/GCM and return Base64 ciphertext.
     *
     * @param plaintext input string
     * @param base64Key AES key (Base64)
     * @param base64Iv GCM IV (Base64)
     * @return Base64 ciphertext
     */
    public static String encryptAesGcmBase64(String plaintext, String base64Key, String base64Iv) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            byte[] ivBytes = Base64.getDecoder().decode(base64Iv);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, AES_ALGO);
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    /**
     * Generate a random IV and return Base64 string.
     *
     * @param lengthBytes iv length in bytes (recommend 12)
     * @return Base64 iv
     */
    public static String generateIvBase64(int lengthBytes) {
        byte[] iv = new byte[lengthBytes];
        new SecureRandom().nextBytes(iv);
        return Base64.getEncoder().encodeToString(iv);
    }

    /**
     * Build HMAC-SHA1 signature with ASCII key sorting and Base64 output.
     *
     * @param params parameters to sign
     * @param signKey merchant sign key
     * @return Base64 signature
     */
    public static String signHmacSha1Base64(Map<String, ?> params, String signKey) {
        if (params == null || signKey == null) {
            return null;
        }
        String stringA = params.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> entry.getKey() + KV_SEPARATOR + entry.getValue())
                .collect(Collectors.joining(PARAM_SEPARATOR));
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1);
            SecretKeySpec keySpec = new SecretKeySpec(signKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA1);
            mac.init(keySpec);
            byte[] digest = mac.doFinal(stringA.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("sign failed", e);
        }
    }
}
