package com.pakgopay.thirdUtil;


import org.springframework.web.bind.annotation.RequestParam;

import java.util.Random;

public class GoogleUtil {

    /**
     * 生成谷歌密钥
     * @return
     */
    public static String getSecretKey() {
        // 根据传入的username查询数据库是否存在该用户的密钥 如果存在，则不生成新密钥
        // 如果不存在密钥，则为新绑定，生成密钥后存入数据库

        return GoogleAuthenticator.getSecretKey();
    }

    public static Long getNum(int length) {
        Random random = new Random();
        int lowerBound = (int) Math.pow(10, length - 1); // 例如，如果length为3，则lowerBound为100
        int upperBound = (int) Math.pow(10, length) - 1; // 例如，如果length为3，则upperBound为999
        return (long)(random.nextInt(upperBound - lowerBound + 1) + lowerBound);
    }

    public static String getQrCode(String secretKey) {
        Long num = getNum(5);
        String base64Pic = QrCodeUtils.creatRrCode(GoogleAuthenticator.getQrCodeText(secretKey,num.toString(),""),200,200);
        return base64Pic;
    }

    public static boolean verifyQrCode(String secretKey, long code) {
        return GoogleAuthenticator.checkCode(secretKey, code, System.currentTimeMillis());
    }

}
