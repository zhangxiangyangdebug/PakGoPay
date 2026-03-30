package com.pakgopay.common.log;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import org.slf4j.Logger;

/**
 * Centralized logging policy for business exceptions.
 * Keep suspicious/critical failures as error, and expected business failures as warn.
 */
public final class LogLevelPolicy {

    private LogLevelPolicy() {
    }

    public static void logBizException(Logger log, String scene, PakGoPayException e) {
        Integer code = e == null || e.getCode() == null ? null : e.getCode().getCode();
        String message = e == null ? null : e.getMessage();
        if (shouldLogError(code)) {
            log.error("{}, code: {} message: {}", scene, code, message);
            return;
        }
        log.warn("{}, code: {} message: {}", scene, code, message);
    }

    public static boolean shouldLogError(Integer code) {
        if (code == null) {
            return true;
        }
        return code.equals(ResultCode.INVALID_TOKEN.getCode())
                || code.equals(ResultCode.FAIL.getCode())
                || code.equals(ResultCode.INTERNAL_SERVER_ERROR.getCode())
                || code.equals(ResultCode.DATA_BASE_ERROR.getCode())
                || code.equals(ResultCode.HTTP_REQUEST_ERROR.getCode());
    }
}
