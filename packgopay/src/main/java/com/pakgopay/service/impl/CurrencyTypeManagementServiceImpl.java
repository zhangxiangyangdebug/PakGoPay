package com.pakgopay.service.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.reqeust.currencyTypeManagement.CurrencyTypeRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.currencyManagement.CurrencyReponse;
import com.pakgopay.mapper.CurrencyTypeMapper;
import com.pakgopay.mapper.UserMapper;
import com.pakgopay.mapper.dto.CurrencyTypeDTO;
import com.pakgopay.service.CurrencyTypeManagementService;
import com.pakgopay.thirdUtil.GoogleUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class CurrencyTypeManagementServiceImpl implements CurrencyTypeManagementService {

    @Autowired
    private CurrencyTypeMapper currencyTypeMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public CommonResponse listCurrencyTypes(CurrencyTypeRequest currencyTypeRequest, HttpServletRequest request) {
       try {
           log.info("start getAllCurrencyType");
           Integer totalNumber = currencyTypeMapper.getCount(currencyTypeRequest);
           if (totalNumber == null) {
               totalNumber = 0;
           }
           List<CurrencyTypeDTO> allCurrencyType = currencyTypeMapper.getAllCurrencyType(currencyTypeRequest);
           CurrencyReponse currencyReponse = new CurrencyReponse();
           currencyReponse.setTotalNumber(totalNumber);
           currencyReponse.setCurrencyTypeDTOList(allCurrencyType);
           currencyReponse.setPageNo(currencyTypeRequest.getPageNo());
           currencyReponse.setPageSize(currencyTypeRequest.getPageSize());
           log.info("end getAllCurrencyType");
           return CommonResponse.success(currencyReponse);
       } catch (Exception e) {
           log.error(e.toString());
           return CommonResponse.fail(ResultCode.FAIL,"get currency type failed");
       }
    }

    @Override
    public CommonResponse createCurrencyType(CurrencyTypeRequest currencyTypeRequest, HttpServletRequest request) {
        try {
            String operatorInfo = verifyGoogleCode(currencyTypeRequest.getGoogleCode(), request);
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
        } catch (DuplicateKeyException e) {
            return CommonResponse.fail(ResultCode.FAIL, "currency already exists");
        } catch (Exception e) {
            return CommonResponse.fail(ResultCode.FAIL,"add currency type failed "+ e.getMessage());
        }
    }

    @Override
    public CommonResponse updateCurrencyType(CurrencyTypeRequest currencyTypeRequest, HttpServletRequest request) {
        try {
            if (currencyTypeRequest.getId() == null) {
                return CommonResponse.fail(ResultCode.FAIL, "currency id is required");
            }
            String operatorInfo = verifyGoogleCode(currencyTypeRequest.getGoogleCode(), request);
            if (operatorInfo == null) {
                return CommonResponse.fail(ResultCode.CODE_IS_EXPIRE);
            }
            String operatorName = operatorInfo.split("&")[1];
            CurrencyTypeDTO currencyTypeDTO = new CurrencyTypeDTO();
            BeanUtils.copyProperties(currencyTypeRequest, currencyTypeDTO);
            currencyTypeDTO.setUpdateTime(System.currentTimeMillis() / 1000);
            currencyTypeDTO.setUpdateBy(operatorName);
            Integer updateResult = currencyTypeMapper.updateCurrencyType(currencyTypeDTO);
            if (updateResult == 1) {
                return CommonResponse.success(ResultCode.SUCCESS);
            }
            return CommonResponse.fail(ResultCode.FAIL, "update currency type failed");
        } catch (PakGoPayException e) {
            return CommonResponse.fail(e);
        } catch (DuplicateKeyException e) {
            return CommonResponse.fail(ResultCode.FAIL, "currency already exists");
        } catch (Exception e) {
            return CommonResponse.fail(ResultCode.FAIL, "update currency type failed " + e.getMessage());
        }
    }

    public String verifyGoogleCode(Long googleCode, HttpServletRequest request) throws PakGoPayException {
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
