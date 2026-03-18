package com.pakgopay.service.impl;

import com.alibaba.excel.EasyExcel;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.data.reqeust.currencyTypeManagement.CurrencyTypeRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.currencyManagement.CurrencyReponse;
import com.pakgopay.data.response.currencyManagement.CurrencySyncResponse;
import com.pakgopay.mapper.CurrencyTypeMapper;
import com.pakgopay.mapper.dto.CurrencyTypeDTO;
import com.pakgopay.mapper.dto.CurrencyTypeSyncExcelRow;
import com.pakgopay.service.CurrencyTypeManagementService;
import com.pakgopay.service.common.CurrencyTimezoneService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class CurrencyTypeManagementServiceImpl implements CurrencyTypeManagementService {
    private static final String CURRENCY_SYNC_EXCEL_LOCATION = "classpath:excel/currency-types.xlsx";

    @Autowired
    private CurrencyTypeMapper currencyTypeMapper;
    @Autowired
    private CurrencyTimezoneService currencyTimezoneService;
    @Autowired
    private ResourceLoader resourceLoader;

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
            insertCurrencyType(currencyTypeDTO);
            return CommonResponse.success(ResultCode.SUCCESS);
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
            CurrencyTypeDTO before = currencyTypeMapper.getCurrencyById(currencyTypeRequest.getId());
            String timezone = normalizeTimezone(currencyTypeRequest.getTimezone());
            String operatorName = currencyTypeRequest.getUserName();
            CurrencyTypeDTO currencyTypeDTO = new CurrencyTypeDTO();
            currencyTypeDTO.setId(currencyTypeRequest.getId());
            currencyTypeDTO.setCurrencyAccuracy(currencyTypeRequest.getCurrencyAccuracy());
            currencyTypeDTO.setTimezone(timezone);
            currencyTypeDTO.setUpdateTime(System.currentTimeMillis() / 1000);
            currencyTypeDTO.setUpdateBy(operatorName);
            Integer updateResult = currencyTypeMapper.updateCurrencyType(currencyTypeDTO);
            if (updateResult == 1) {
                if (before != null && before.getCurrencyType() != null) {
                    currencyTimezoneService.refreshCurrencyTimezoneCache(before.getCurrencyType());
                }
                if (currencyTypeDTO.getCurrencyType() != null) {
                    currencyTimezoneService.refreshCurrencyTimezoneCache(currencyTypeDTO.getCurrencyType());
                }
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

    @Override
    public CommonResponse syncCurrencyTypes(CurrencyTypeRequest currencyTypeRequest, HttpServletRequest request) {
        try {
            Resource resource = resourceLoader.getResource(CURRENCY_SYNC_EXCEL_LOCATION);
            if (!resource.exists()) {
                return CommonResponse.fail(ResultCode.FAIL,
                        "currency sync excel not found: " + CURRENCY_SYNC_EXCEL_LOCATION);
            }

            List<CurrencyTypeSyncExcelRow> rows;
            try (InputStream inputStream = resource.getInputStream()) {
                rows = EasyExcel.read(inputStream)
                        .head(CurrencyTypeSyncExcelRow.class)
                        .sheet()
                        .doReadSync();
            }

            List<String> skippedCurrencies = new ArrayList<>();
            List<String> invalidRows = new ArrayList<>();
            int insertedCount = 0;
            int totalRows = rows == null ? 0 : rows.size();

            if (rows != null) {
                for (int i = 0; i < rows.size(); i++) {
                    CurrencyTypeSyncExcelRow row = rows.get(i);
                    String invalidReason = validateSyncRow(row);
                    if (invalidReason != null) {
                        invalidRows.add("row " + (i + 2) + ": " + invalidReason);
                        continue;
                    }

                    String currencyType = normalizeCurrencyType(row.getCurrencyType());
                    if (currencyTypeMapper.getCurrencyByCurrencyType(currencyType) != null) {
                        skippedCurrencies.add(currencyType);
                        continue;
                    }

                    CurrencyTypeDTO currencyTypeDTO = new CurrencyTypeDTO();
                    currencyTypeDTO.setCurrencyType(currencyType);
                    currencyTypeDTO.setName(row.getName().trim());
                    currencyTypeDTO.setIcon(row.getIcon().trim());
                    currencyTypeDTO.setCurrencyAccuracy(row.getCurrencyAccuracy());
                    currencyTypeDTO.setTimezone(normalizeTimezone(row.getTimezone()));
                    currencyTypeDTO.setCreateBy(resolveOperatorName(currencyTypeRequest));
                    currencyTypeDTO.setUpdateBy(resolveOperatorName(currencyTypeRequest));
                    insertCurrencyType(currencyTypeDTO);
                    insertedCount++;
                }
            }

            CurrencySyncResponse response = new CurrencySyncResponse(
                    CURRENCY_SYNC_EXCEL_LOCATION,
                    totalRows,
                    insertedCount,
                    skippedCurrencies.size(),
                    invalidRows.size(),
                    skippedCurrencies,
                    invalidRows
            );
            return CommonResponse.success(response);
        } catch (IllegalArgumentException e) {
            return CommonResponse.fail(ResultCode.ORDER_PARAM_VALID, e.getMessage());
        } catch (Exception e) {
            log.error("sync currency types failed", e);
            return CommonResponse.fail(ResultCode.FAIL, "sync currency type failed " + e.getMessage());
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

    private void insertCurrencyType(CurrencyTypeDTO currencyTypeDTO) {
        long now = System.currentTimeMillis() / 1000;
        if (currencyTypeDTO.getCreateTime() == null) {
            currencyTypeDTO.setCreateTime(now);
        }
        if (currencyTypeDTO.getUpdateTime() == null) {
            currencyTypeDTO.setUpdateTime(now);
        }
        Integer addResult = currencyTypeMapper.addNewCurrency(currencyTypeDTO);
        if (addResult == null || addResult != 1) {
            throw new IllegalStateException("insert currency type failed");
        }
        currencyTimezoneService.refreshCurrencyTimezoneCache(currencyTypeDTO.getCurrencyType());
    }

    private String validateSyncRow(CurrencyTypeSyncExcelRow row) {
        if (row == null) {
            return "empty row";
        }
        if (row.getCurrencyType() == null || row.getCurrencyType().trim().isEmpty()) {
            return "currencyType is empty";
        }
        if (row.getName() == null || row.getName().trim().isEmpty()) {
            return "name is empty";
        }
        if (row.getIcon() == null || row.getIcon().trim().isEmpty()) {
            return "icon is empty";
        }
        if (row.getCurrencyAccuracy() == null) {
            return "currencyAccuracy is empty";
        }
        if (row.getTimezone() == null || row.getTimezone().trim().isEmpty()) {
            return "timezone is empty";
        }
        normalizeTimezone(row.getTimezone());
        return null;
    }

    private String normalizeCurrencyType(String currencyType) {
        return currencyType == null ? null : currencyType.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveOperatorName(CurrencyTypeRequest currencyTypeRequest) {
        if (currencyTypeRequest == null) {
            return "system";
        }
        if (currencyTypeRequest.getUserName() != null && !currencyTypeRequest.getUserName().trim().isEmpty()) {
            return currencyTypeRequest.getUserName().trim();
        }
        if (currencyTypeRequest.getUserId() != null && !currencyTypeRequest.getUserId().trim().isEmpty()) {
            return currencyTypeRequest.getUserId().trim();
        }
        return "system";
    }
}
