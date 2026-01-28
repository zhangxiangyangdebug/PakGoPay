package com.pakgopay.data.reqeust.systemConfig;

import com.pakgopay.data.reqeust.BaseRequest;
import lombok.Builder;
import lombok.Data;

@Data
public class LoginUserRequest extends BaseRequest {
    private String loginName;
    private Integer pageSize;
    private Integer pageNo;
    public Integer getOffSet() {
        return (pageNo - 1) * pageSize;
    }
}
