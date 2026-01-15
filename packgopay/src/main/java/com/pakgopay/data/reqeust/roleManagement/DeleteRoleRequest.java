package com.pakgopay.data.reqeust.roleManagement;

import com.pakgopay.data.reqeust.BaseRequest;
import lombok.Data;

@Data
public class DeleteRoleRequest extends BaseRequest {
    private Integer roleId;
}
