package com.pakgopay.util;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Encryption utilities (AES/GCM).
 */
public class CryptoUtil {

    private static final String AES_ALGO = "AES";
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final String HMAC_SHA1 = "HmacSHA1";
    private static final String KV_SEPARATOR = "=";
    private static final String PARAM_SEPARATOR = "&";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final String AES_GCM_V1_PREFIX = "gcm:v1:";
    // TODO move to environment variable / KMS.
    private static final String AES_GCM_MASTER_KEY_BASE64 = "BKl/bDfV5anMbuDj7mZRjbk29ln0YLHU9JoTOhSg9P4=";
    private static final SecretKeySpec AES_GCM_MASTER_KEY_SPEC = buildAesKeySpec(AES_GCM_MASTER_KEY_BASE64);

    /**
     * Encrypt plaintext with built-in AES-GCM master key.
     *
     * @param plaintext plaintext
     * @return encrypted text in format gcm:v1:Base64(iv+ciphertext+tag)
     */
    public static String encryptWithMasterKey(String plaintext) {
        return encryptWithMasterKey(plaintext, null);
    }

    /**
     * Encrypt plaintext with built-in AES-GCM master key and optional AAD.
     *
     * @param plaintext plaintext
     * @param aad associated authenticated data
     * @return encrypted text in format gcm:v1:Base64(iv+ciphertext+tag)
     */
    public static String encryptWithMasterKey(String plaintext, String aad) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(Cipher.ENCRYPT_MODE, AES_GCM_MASTER_KEY_SPEC, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            if (aad != null) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return AES_GCM_V1_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("encrypt with master key failed", e);
        }
    }

    /**
     * Encrypt merchant signKey with AES-GCM master key.
     */
    public static String encryptSignKey(String signKey) {
        return encryptWithMasterKey(signKey);
    }

    /**
     * Decrypt ciphertext encrypted by {@link #encryptWithMasterKey(String)}.
     *
     * @param encrypted encrypted text
     * @return plaintext
     */
    public static String decryptWithMasterKey(String encrypted) {
        return decryptWithMasterKey(encrypted, null);
    }

    /**
     * Decrypt ciphertext encrypted by {@link #encryptWithMasterKey(String, String)}.
     *
     * @param encrypted encrypted text
     * @param aad associated authenticated data
     * @return plaintext
     */
    public static String decryptWithMasterKey(String encrypted, String aad) {
        if (encrypted == null) {
            return null;
        }
        if (!encrypted.startsWith(AES_GCM_V1_PREFIX)) {
            throw new IllegalArgumentException("unsupported encrypted text format");
        }
        try {
            String raw = encrypted.substring(AES_GCM_V1_PREFIX.length());
            byte[] payload = Base64.getDecoder().decode(raw);
            if (payload.length <= GCM_IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("invalid encrypted payload");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, GCM_IV_LENGTH_BYTES);
            byte[] cipherText = Arrays.copyOfRange(payload, GCM_IV_LENGTH_BYTES, payload.length);

            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(Cipher.DECRYPT_MODE, AES_GCM_MASTER_KEY_SPEC, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            if (aad != null) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
            byte[] plaintext = cipher.doFinal(cipherText);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decrypt with master key failed", e);
        }
    }

    /**
     * Decrypt merchant signKey with AES-GCM master key.
     */
    public static String decryptSignKey(String encryptedSignKey) {
        return decryptWithMasterKey(encryptedSignKey);
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
                .filter(entry -> {
                    Object value = entry.getValue();
                    if (value instanceof String s) {
                        return !s.isBlank();
                    }
                    return true;
                })
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

    private static SecretKeySpec buildAesKeySpec(String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
                throw new IllegalArgumentException("invalid AES key length");
            }
            return new SecretKeySpec(keyBytes, AES_ALGO);
        } catch (Exception e) {
            throw new IllegalStateException("invalid AES master key", e);
        }
    }
}
