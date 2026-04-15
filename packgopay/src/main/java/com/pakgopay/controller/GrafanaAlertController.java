package com.pakgopay.controller;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.service.common.GrafanaAlertService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/pakGoPay/server/grafana")
public class GrafanaAlertController {

    private final GrafanaAlertService grafanaAlertService;

    public GrafanaAlertController(GrafanaAlertService grafanaAlertService) {
        this.grafanaAlertService = grafanaAlertService;
    }

    @PostMapping("/alert")
    public CommonResponse alert(@RequestBody String payload,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        GrafanaAlertService.ProcessResult result = grafanaAlertService.process(payload, request);
        if (result.isSuccess()) {
            return CommonResponse.success(result.asResponsePayload());
        }
        response.setStatus(result.getHttpStatus());
        if (result.getHttpStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            return CommonResponse.fail(ResultCode.SC_UNAUTHORIZED, result.getMessage());
        }
        if (result.getHttpStatus() == HttpServletResponse.SC_BAD_REQUEST) {
            return CommonResponse.fail(ResultCode.INVALID_PARAMS, result.getMessage());
        }
        return CommonResponse.fail(ResultCode.FAIL, result.getMessage());
    }
}

