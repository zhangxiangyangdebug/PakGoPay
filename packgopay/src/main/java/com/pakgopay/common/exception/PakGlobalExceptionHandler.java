package com.pakgopay.common.exception;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.data.response.CommonResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
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
        String msg = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse(ResultCode.INVALID_PARAMS.getMessage());
        log.error("MethodArgumentNotValidException {}", msg);
        return CommonResponse.fail(ResultCode.INVALID_PARAMS, msg);
    }

    /**
     * 2、@RequestParam / @PathVariable 校验异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public CommonResponse<Void> handleParamValid(ConstraintViolationException e) {
        String msg = e.getConstraintViolations()
                .stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse(ResultCode.INVALID_PARAMS.getMessage());
        log.error("ConstraintViolationException {}", msg);
        return CommonResponse.fail(ResultCode.INVALID_PARAMS, msg);
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
        log.error("Exception ", e);
        return CommonResponse.fail(ResultCode.FAIL, e.getMessage());
    }

}
