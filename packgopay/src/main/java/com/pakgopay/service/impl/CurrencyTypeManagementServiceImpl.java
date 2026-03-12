package com.pakgopay.service.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.data.reqeust.currencyTypeManagement.CurrencyTypeRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.currencyManagement.CurrencyReponse;
import com.pakgopay.mapper.CurrencyTypeMapper;
import com.pakgopay.mapper.dto.CurrencyTypeDTO;
import com.pakgopay.service.CurrencyTypeManagementService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;

@Service
@Slf4j
public class CurrencyTypeManagementServiceImpl implements CurrencyTypeManagementService {

    @Autowired
    private CurrencyTypeMapper currencyTypeMapper;

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
            String timezone = normalizeTimezone(currencyTypeRequest.getTimezone());
            currencyTypeRequest.setTimezone(timezone);
            CurrencyTypeDTO currencyTypeDTO = new CurrencyTypeDTO();
            BeanUtils.copyProperties(currencyTypeRequest, currencyTypeDTO);
            Integer addResult = currencyTypeMapper.addNewCurrency(currencyTypeDTO);
            if (addResult == 1) {
                return CommonResponse.success(ResultCode.SUCCESS);
            } else {
                return CommonResponse.fail(ResultCode.FAIL);
            }
        } catch (DuplicateKeyException e) {
            return CommonResponse.fail(ResultCode.FAIL, "currency already exists");
        } catch (IllegalArgumentException e) {
            return CommonResponse.fail(ResultCode.ORDER_PARAM_VALID, e.getMessage());
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
            String timezone = normalizeTimezone(currencyTypeRequest.getTimezone());
            currencyTypeRequest.setTimezone(timezone);
            String operatorName = currencyTypeRequest.getUserName();
            CurrencyTypeDTO currencyTypeDTO = new CurrencyTypeDTO();
            BeanUtils.copyProperties(currencyTypeRequest, currencyTypeDTO);
            currencyTypeDTO.setUpdateTime(System.currentTimeMillis() / 1000);
            currencyTypeDTO.setUpdateBy(operatorName);
            Integer updateResult = currencyTypeMapper.updateCurrencyType(currencyTypeDTO);
            if (updateResult == 1) {
                return CommonResponse.success(ResultCode.SUCCESS);
            }
            return CommonResponse.fail(ResultCode.FAIL, "update currency type failed");
        } catch (DuplicateKeyException e) {
            return CommonResponse.fail(ResultCode.FAIL, "currency already exists");
        } catch (IllegalArgumentException e) {
            return CommonResponse.fail(ResultCode.ORDER_PARAM_VALID, e.getMessage());
        } catch (Exception e) {
            return CommonResponse.fail(ResultCode.FAIL, "update currency type failed " + e.getMessage());
        }
    }

    private String normalizeTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return null;
        }
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid timezone: " + timezone);
        }
    }
}
