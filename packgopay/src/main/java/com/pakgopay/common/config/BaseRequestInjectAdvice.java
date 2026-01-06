package com.pakgopay.common.config;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.reqeust.BaseRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

@ControllerAdvice
public class BaseRequestInjectAdvice extends RequestBodyAdviceAdapter {

    @Override
    public boolean supports(MethodParameter methodParameter,
                            Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        // 只处理继承 BaseRequest 的请求体
        if (targetType instanceof Class<?> clazz) {
            return BaseRequest.class.isAssignableFrom(clazz);
        }
        return false;
    }

    @Override
    public Object afterBodyRead(Object body,
                                HttpInputMessage inputMessage,
                                MethodParameter parameter,
                                Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {

        if (body instanceof BaseRequest req) {
            HttpServletRequest request =
                    ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                            .getRequest();

            Object ip = request.getAttribute(CommonConstant.ATTR_IP);
            if (ip != null) {
                req.setClientIp(ip.toString());
            }

            Object userId = request.getAttribute(CommonConstant.ATTR_USER_ID);
            if (userId != null) {
                req.setUserId(userId.toString());
            }

        }

        return body;
    }
}

