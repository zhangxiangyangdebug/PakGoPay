package com.pakgopay.util;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * API key and sign key management helpers.
 */
public class KeySignManager {

    private static final int API_KEY_BYTES = 24;
    private static final int SIGN_KEY_BYTES = 32;

    /**
     * Generate an API key (Base64 URL-safe).
     *
     * @return api key
     */
    public static String generateApiKey() {
        return generateBase64UrlKey(API_KEY_BYTES);
    }

    /**
     * Generate a sign key (Base64 URL-safe).
     *
     * @return sign key
     */
    public static String generateSignKey() {
        return generateBase64UrlKey(SIGN_KEY_BYTES);
    }

    /**
     * Generate apiKey and signKey together.
     *
     * @return key pair
     */
    public static KeyPairResult generateKeyPair() {
        return new KeyPairResult(generateApiKey(), generateSignKey());
    }

    /**
     * Sign parameters with HMAC-SHA1 and Base64 output.
     *
     * @param params parameters
     * @param signKey sign key
     * @return signature
     */
    public static String sign(Map<String, ?> params, String signKey) {
        return EncryptUtil.signHmacSha1Base64(params, signKey);
    }

    private static String generateBase64UrlKey(int lengthBytes) {
        byte[] bytes = new byte[lengthBytes];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static class KeyPairResult {
        private final String apiKey;
        private final String signKey;

        public KeyPairResult(String apiKey, String signKey) {
            this.apiKey = apiKey;
            this.signKey = signKey;
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getSignKey() {
            return signKey;
        }
    }
}
