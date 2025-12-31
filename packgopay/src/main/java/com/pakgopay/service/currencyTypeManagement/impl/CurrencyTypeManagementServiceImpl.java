package com.pakgopay.service.currencyTypeManagement.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.currencyTypeManagement.CurrencyTypeRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.mapper.CurrencyTypeMapper;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.mapper.dto.CurrencyTypeDTO;
import com.pakgopay.service.common.AuthorizationService;
import com.pakgopay.service.currencyTypeManagement.CurrencyTypeManagementService;
import com.pakgopay.thirdUtil.GoogleUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CurrencyTypeManagementServiceImpl implements CurrencyTypeManagementService {

    @Autowired
    private CurrencyTypeMapper currencyTypeMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public CommonResponse getAllCurrencyType() {
       try {
           List<CurrencyTypeDTO> allCurrencyType = currencyTypeMapper.getAllCurrencyType();
           System.out.println(allCurrencyType.toString());
           return CommonResponse.success(allCurrencyType);
       } catch (Exception e) {
           return CommonResponse.fail(ResultCode.FAIL,"get currency type failed");
       }
    }

    @Override
    public CommonResponse getCurrencyById(Integer id) {
        try{
            List<CurrencyTypeDTO> allCurrencyType= new ArrayList<>();
            if (id == null) {
                allCurrencyType = currencyTypeMapper.getAllCurrencyType();
            } else {
                allCurrencyType = currencyTypeMapper.getCurrencyById(id);
            }
            return CommonResponse.success(allCurrencyType);
        } catch (Exception e) {
            return CommonResponse.fail(ResultCode.FAIL,"get currency type failed");
        }


    }

    @Override
    public CommonResponse addNewCurrencyType(CurrencyTypeRequest currencyTypeRequest, HttpServletRequest request) {
        try {
            String operatorInfo = checkGoogleCode(currencyTypeRequest.getGoogleCode(), request);
            if (operatorInfo == null) {
                return CommonResponse.fail(ResultCode.CODE_IS_EXPIRE);
            }
            CurrencyTypeDTO currencyTypeDTO = new CurrencyTypeDTO();
            BeanUtils.copyProperties(currencyTypeRequest, currencyTypeDTO);
            Integer addResult = currencyTypeMapper.addNewCurrency(currencyTypeDTO);
            if (addResult == 1) {
                return CommonResponse.success(ResultCode.SUCCESS);
            } else {
                return CommonResponse.fail(ResultCode.FAIL);
            }
        } catch (PakGoPayException e) {
            return CommonResponse.fail(e);
        } catch (Exception e) {
            return CommonResponse.fail(ResultCode.FAIL,"add currency type failed "+ e.getMessage());
        }
    }


    public String checkGoogleCode(Long googleCode, HttpServletRequest request) throws PakGoPayException {
        String userInfo = GoogleUtil.getUserInfoFromToken(request);
        if(userInfo==null){
            throw new PakGoPayException(ResultCode.TOKEN_IS_EXPIRE);
        }
        String operator = userInfo.split("&")[0];
        String secretKey = null;
        try {
            secretKey = userMapper.getSecretKeyByUserId(operator);
        } catch (Exception e) {
            throw new PakGoPayException(ResultCode.FAIL,"get secret key for operator failed");
        }
        if(GoogleUtil.verifyQrCode(secretKey, googleCode)){
            return userInfo;
        }
        return null;
    }
}
