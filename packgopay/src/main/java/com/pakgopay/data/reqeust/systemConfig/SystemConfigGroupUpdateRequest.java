package com.pakgopay.data.reqeust.systemConfig;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SystemConfigGroupUpdateRequest {
    @NotBlank(message = "group is empty")
    private String group;

    @NotEmpty(message = "configItems is empty")
    @Valid
    private List<SystemConfigGroupItemRequest> configItems;
}

