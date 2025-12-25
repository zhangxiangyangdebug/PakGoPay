package com.pakgopay.service.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jose4j.json.JsonUtil;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.util.UUID;

@Service
public class AuthorizationService {

    private static Logger logger = LogManager.getLogger("RollingFile");

    private static String keyId = "99cb0214af02457b9480c579b8a010b2";
    private static String privateKeyStr = "{\"kty\":\"RSA\",\"kid\":\"99cb0214af02457b9480c579b8a010b2\",\"alg\":\"ES256\",\"n\":\"nl9K4mA1BUKaTXa1y3WHsX_tIZWF6hTX3bw-jz7KmLHhf5F8_h_NKVdwDgJQMzcHiO8hZSwTzvrOREsoUwgIz7dtDWP_DOdgMeySN7IkBro7ODPtn23N0lyHtnp9RVOmRTb5rUt5F4UizJn5FzeLQlvb5KKg80n7d3iA6ePq2_YPVOmAamNJXgEQ6xr9g0apaqxG1Q-KcZ8-8MHZEqocCOXGlpfmS-SZqq7SMkP3AvLGC1ayd52YzsSyitGFEkkiZyDVefBU8VjKj8wEtqs3Qmr43OUDVev-e7kisBJVr0FaDIoMY2_Y1gLmsKNul6k9204Xo9qM3fif-aBqPthtcw\",\"e\":\"AQAB\",\"d\":\"Hnz41RCxVFRokR4XzFXRbBXVCECZOaGOlwS8xGXRqcigwSa4edGUdO98xm2ik-F1pWJnuurb215EJDiVyDpFO_uzVj8YZBsM1T-WPHuiXNswOFci2SzR0Tv38mStRhHe8fct5LMhWipKszm3DsWmHP3DHMZEFuH4s5yysJQsa_CqWThI5YDWSmLT9e13AUxIqd-E9eCSVL6TPGx1TNAE0W4UEmovJTJvZtkCEmB9idsF_kvx_lc1GBxMU2zQd9lcbJt0Kf0wQbAH-vHOFwns6THBDsghemWb0pcHTRKRqxpPJstO6N05WsYlgg9mT6mIprTqYUAqwdJHIyRGD_ySOQ\",\"p\":\"uEaEbPpZPrbLYAn_JA7emOoxyWa4ANKyJlrmDFsL3UXCT6jFcfiTPLPrXI0Qn0gsFG8if7g-L-4jcX4f2ExexQLOfX7JMD-uFr7SgNT4JlanCS-5NxKkrWaoEIqHsIDKrRImMJVB_exqWxt8FMmp9Dm_qdrlYDotG1JRHTwx6is\",\"q\":\"3AO9ScfsqiKQ1zlTLx4VTHfpeW-RouWXURNDMo_XbrC8nsKY6IDb7XhKz8Jtpni1L5HLwVDQTzvEw7K2FIb6A4D-wo9kF5lIDgpNAFbHEFSA66NJHOsksi4ozaR-gaN2F51goNOQSrxRFczLD2_zvFnTo6oAxTzZNIlfU5ROTdk\",\"dp\":\"mPR_au2KMlIMEZV6n_VvssGBvchzJsu8b7W3ib3w5SPIG9LcwLMxk4tL6zB0AaIoZHWyzwyhIauq48Nqr2EEsMaZmvUoDdJtsBeIJsOfjkMStr9UH3BsHDo_eitiYZElqe6MoHrjod8gkKsJnT-ewEdG3bgB-JORaUI0be3PCK0\",\"dq\":\"HYri_-U4gh_iEwQ_hzQi6AGx9_xjMdxiVSChBUkLRHG24hp_LbkfzuM4KG2TC6dprNmG4o5Xakw8P4rfqCH3sEDB9J4Mcp7CbiAz9rewQyEVro5eYuOwKbzI_dP4qK-qS8F2GCJQXbirqqG1In2F3OSy5mcNc26YO9W2VyKy4QE\",\"qi\":\"J6cJmnMG805dml-9prJovdaM3MfqpApxfap3QzClE94t0Xle-448ZHo-cA9zIz-h_XpUFJB_U86gfh37E-kqTWUrmlOMKy5siWYy5SdvPh5pEbhalY24duTAhr3XERU0npV-u3qL7T0NdpXpxLJpnotOkFY22cJjM5Ii1qrJGuY\"}";
    private static String publicKeyStr = "{\"kty\":\"RSA\",\"kid\":\"99cb0214af02457b9480c579b8a010b2\",\"alg\":\"ES256\",\"n\":\"nl9K4mA1BUKaTXa1y3WHsX_tIZWF6hTX3bw-jz7KmLHhf5F8_h_NKVdwDgJQMzcHiO8hZSwTzvrOREsoUwgIz7dtDWP_DOdgMeySN7IkBro7ODPtn23N0lyHtnp9RVOmRTb5rUt5F4UizJn5FzeLQlvb5KKg80n7d3iA6ePq2_YPVOmAamNJXgEQ6xr9g0apaqxG1Q-KcZ8-8MHZEqocCOXGlpfmS-SZqq7SMkP3AvLGC1ayd52YzsSyitGFEkkiZyDVefBU8VjKj8wEtqs3Qmr43OUDVev-e7kisBJVr0FaDIoMY2_Y1gLmsKNul6k9204Xo9qM3fif-aBqPthtcw\",\"e\":\"AQAB\"}";
    public static long accessTokenExpirationTime = 60*30; //单位：秒
    public static long refreshTokenExpirationTime = 60*60*24*7;

    public String createAccessIdToken(String userId, String userName) {
        return createIdToken(userId,userName, accessTokenExpirationTime);
    }

    public String createRefreshToken(String userId, String userName) {
        return createIdToken(userId, userName, refreshTokenExpirationTime);
    }

    public String createIdToken(String account, String userName, long expireTime){

        try {
            JwtClaims jwtClaims = new JwtClaims();
            jwtClaims.setGeneratedJwtId();
            jwtClaims.setIssuedAtToNow();
            // expire time
            NumericDate date = NumericDate.now();
            date.addSeconds(expireTime);
            jwtClaims.setExpirationTime(date);
            jwtClaims.setNotBeforeMinutesInThePast(1);
            jwtClaims.setSubject(account);
            jwtClaims.setClaim("account", account);
            jwtClaims.setClaim("userName", userName);
            jwtClaims.setAudience("PakGoPay");
            //jwtClaims.setIssuer(String.valueOf(account)); //token和用户强绑定

            JsonWebSignature jws = new JsonWebSignature();
            jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
            jws.setKeyIdHeaderValue(keyId);
            jws.setPayload(jwtClaims.toJson());
            PrivateKey privateKey = new RsaJsonWebKey(JsonUtil.parseJson(privateKeyStr)).getPrivateKey();
            jws.setKey(privateKey);

            // 获取token
            String accessToken = jws.getCompactSerialization();
            System.out.println("accessToken:"+accessToken);
            return accessToken;
        } catch (JoseException e) {
            return null;
        }

    }

    public static String verifyToken(String token){
        try {
            JwtConsumer consumer = new JwtConsumerBuilder()
                    .setRequireExpirationTime()
                    .setMaxFutureValidityInMinutes(525600)
                    .setAllowedClockSkewInSeconds(30) //过期后30还能解析成功
                    .setRequireSubject()   //令牌接收方，不匹配则不予接受
                    .setExpectedAudience("PakGoPay")
                    .setVerificationKey(new RsaJsonWebKey(JsonUtil.parseJson(publicKeyStr)).getPublicKey())
                    .build();
            JwtClaims claims = consumer.processToClaims(token);
            if (claims != null) {
                String account = (String) claims.getClaimValue("account");
                String userName = (String) claims.getClaimValue("userName");
                System.out.println("认证通过， token payload携带的自定义内容：用户账号account=" + account);
                return account+"&"+userName;
            }
        } catch (JoseException | InvalidJwtException e) {
            logger.error("verify token failed: {}", e.getMessage());
        }
        return null;
    }

    // 生成公钥 私钥
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
        //TokenUtils.createKeyPair();
        /*String idToken = new AuthorizationService().createIdToken("leealank4@gmail.com", 30);
        System.out.println(idToken);
        String result = new AuthorizationService().verifyToken(idToken);
        System.out.println(result);*/
    }
}
