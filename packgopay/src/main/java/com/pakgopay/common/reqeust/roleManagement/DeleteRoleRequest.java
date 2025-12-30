package com.pakgopay.common.reqeust.roleManagement;

import com.pakgopay.common.reqeust.BaseRequest;
import lombok.Data;

@Data
public class DeleteRoleRequest extends BaseRequest {
    private Integer roleId;
}
