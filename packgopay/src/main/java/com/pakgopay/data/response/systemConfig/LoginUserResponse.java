package com.pakgopay.data.response.systemConfig;

import com.pakgopay.mapper.dto.UserDTO;
import lombok.Data;

import java.util.List;

@Data
public class LoginUserResponse {
    List<UserDTO> loginUsers;
    Integer totalNumber;
    Integer pageNo;
    Integer pageSize;
}
