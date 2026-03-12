package com.pakgopay.data.reqeust.systemConfig;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SystemConfigGroupItemRequest {
    @NotBlank(message = "key is empty")
    private String key;

    private Object value;
}

