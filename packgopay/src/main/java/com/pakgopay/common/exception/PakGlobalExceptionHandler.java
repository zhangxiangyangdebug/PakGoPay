package com.pakgopay.common.exception;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.response.CommonResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class PakGlobalExceptionHandler {

    /**
     * 1、RequestBody 参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResponse<Void> handleBodyValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        log.error("MethodArgumentNotValidException {}", msg);
        return CommonResponse.fail(ResultCode.FAIL, msg);
    }

    /**
     * 2、@RequestParam / @PathVariable 校验异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public CommonResponse<Void> handleParamValid(ConstraintViolationException e) {
        log.error("ConstraintViolationException {}", e.getMessage());
        return CommonResponse.fail(ResultCode.FAIL, e.getMessage());
    }

    /**
     * 3、业务异常
     */
    @ExceptionHandler(PakGoPayException.class)
    public CommonResponse<Void> handleBiz(PakGoPayException e) {
        log.error("PakGoPayException {}", e.getMessage());
        return CommonResponse.fail(e.getCode(), e.getMessage());
    }

    /**
     * 4、系统异常（兜底）
     */
    @ExceptionHandler(Exception.class)
    public CommonResponse<Void> handleException(Exception e) {
        log.error("Exception {}", e.getMessage());
        return CommonResponse.fail(ResultCode.FAIL, ResultCode.FAIL.getMessage());
    }

}
