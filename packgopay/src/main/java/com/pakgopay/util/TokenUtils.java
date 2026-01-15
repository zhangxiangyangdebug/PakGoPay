package com.pakgopay.util;

public class TokenUtils {
    /*private static final String SECRET_KEY = "wKkFKKTPpWtDq9C7cDqN8d7833T6C1xG6y9Z36ypv/lXK0cH0epj2zwaIgOGzlCdT7+ZY2GoCQWouHgDtfxPIkDxPDIVEwgqOq7yXiSLUvANubPW4tTG9MtSEskiUqEdT2YtWDTpYy8kjxSjx9L9fJ1fHq4MHwWspwvENoTyC8Q=";
    private static final long EXPIRATION_TIME = 1800000;
    private static final String TOKEN_PREFIX = "Bearer ";

    public static boolean  validateToken(String token) {
        Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
        JWTVerifier verifier = JWT.require(algorithm).build();
        try {
            DecodedJWT jwt = verifier.verify(token);
            jwt.getClaim("userId").asString();
            return true;
        } catch (JWTVerificationException exception) {
            return false;
        }

    }

    public static String generateToken(String username) {
        String token = "";
        LocalDateTime now = LocalDateTime.now();
        String dateTimeString = now.getYear() + String.format("%02d", now.getMonthValue()) + String.format("%02d", now.getDayOfMonth())
                + String.format("%02d", now.getHour()) + String.format("%02d", now.getMinute()) +
                String.format("%02d", now.getSecond());
        Random random = new Random();
        int randomNumber = random.nextInt(90000) + 10000;
        // 拼接token
        token = token + dateTimeString + randomNumber;
        return token;

    }

    public static String getToken(String userId) {
        Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
        TimeZone tz = TimeZone.getTimeZone("Asia/Shanghai");
        Calendar instance = Calendar.getInstance();
        instance.setTimeZone(tz);
        System.out.println(instance.getTime());
        // 设置过期时间
        //instance.add(Calendar.DATE, 1);
        instance.add(Calendar.MINUTE, 10);
        JWTCreator.Builder builder = JWT.create();

        return  builder.withClaim("userId", userId)
                .withExpiresAt(instance.getTime())
                .sign(algorithm);
    }

    public static void createKeyPair() {
        String keyId = UUID.randomUUID().toString().replaceAll("-", "");
        RsaJsonWebKey jwk = null;
        try{
            jwk = RsaJwkGenerator.generateJwk(2048);
        } catch (Exception e){
            e.printStackTrace();
        }
        jwk.setKeyId(keyId);
        jwk.setAlgorithm(AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256);
        String publicKey = jwk.toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY);
        String privateKey = jwk.toJson(JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE);
        System.out.println("keyId: " + keyId);
        System.out.println("publicKey: " + publicKey);
        System.out.println("privateKey: " + privateKey);
    }

    public static void main(String[] args) {
        TokenUtils.createKeyPair();
    }*/
    public static void main(String[] args) {
      /*  MenuItem menuItem = new MenuItem();
        menuItem.setMenuId("1");
        menuItem.setPath("sss");
        menuItem.setIcon("sssadf");
        menuItem.setMenuLevel(1);
        menuItem.setMenuName("主页");
        menuItem.setParentId("001");
        Extra extra = new Extra();
        extra.setNeedLogin(true);
        extra.setTitle("sss");
        menuItem.setMeta(extra);
        Children children = new Children();
        children.setMenuId("001");
        System.out.println(new Gson().toJson(menuItem));*/

    }
}
