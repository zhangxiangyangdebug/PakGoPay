package com.pakgopay.service.impl;

import com.pakgopay.common.enums.OperateInterfaceEnum;
import com.pakgopay.data.reqeust.bankCode.BankCodeQueryRequest;
import com.pakgopay.data.reqeust.bankCode.PaymentBankCodeItemRequest;
import com.pakgopay.data.reqeust.bankCode.PaymentBankCodeUpdateRequest;
import com.pakgopay.data.reqeust.bankCode.PaymentBankCodeQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.bankCode.BankCodeQueryResponse;
import com.pakgopay.data.response.bankCode.BankCodeSyncResponse;
import com.pakgopay.data.response.bankCode.PaymentBankCodeQueryResponse;
import com.pakgopay.mapper.BankCodeDictMapper;
import com.pakgopay.mapper.PaymentChannelBankMapper;
import com.pakgopay.mapper.dto.BankCodeDictDto;
import com.pakgopay.mapper.dto.BankCodeSyncExcelRow;
import com.pakgopay.mapper.dto.PaymentBankCodeDto;
import com.pakgopay.mapper.dto.PaymentChannelBankDto;
import com.pakgopay.service.BankCodeService;
import com.pakgopay.service.common.OperateLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Slf4j
@Service
public class BankCodeServiceImpl implements BankCodeService {

    @Autowired
    private BankCodeDictMapper bankCodeDictMapper;

    @Autowired
    private PaymentChannelBankMapper paymentChannelBankMapper;

    @Autowired
    private OperateLogService operateLogService;

    @Override
    /**
     * Query bank dictionary data by conditions with pagination.
     */
    public CommonResponse queryBankCode(BankCodeQueryRequest request) {
        Integer totalNumber = bankCodeDictMapper.countByQuery(request);
        List<BankCodeDictDto> list = bankCodeDictMapper.pageByQuery(request);

        BankCodeQueryResponse response = new BankCodeQueryResponse();
        response.setTotalNumber(totalNumber);
        response.setPageNo(request.getPageNo());
        response.setPageSize(request.getPageSize());
        response.setBankCodeDictDtoList(list);
        return CommonResponse.success(response);
    }

    @Override
    /**
     * Query all bank codes under a currency and mark selected/status state for one payment channel.
     */
    public CommonResponse queryPaymentBankCode(PaymentBankCodeQueryRequest request) {
        List<PaymentBankCodeDto> list = bankCodeDictMapper.listByPaymentCurrency(request);

        PaymentBankCodeQueryResponse response = new PaymentBankCodeQueryResponse();
        response.setTotalNumber(list == null ? 0 : list.size());
        response.setPaymentBankCodeDtoList(list);
        return CommonResponse.success(response);
    }

    @Override
    @Transactional
    /**
     * Batch update payment-channel bank-code relations by full-set sync.
     * <p>
     * The request items are treated as target set under paymentId + currencyCode.
     * DB rows not present in request will be deleted.
     */
    public CommonResponse updatePaymentBankCodes(PaymentBankCodeUpdateRequest request) {
        long now = System.currentTimeMillis() / 1000;
        String currencyCode = request.getCurrencyCode().trim();

        // 1) Normalize request items into a unique target map by (bankCode, supportType).
        Map<String, PaymentBankCodeItemRequest> targetMap = new LinkedHashMap<>();
        for (PaymentBankCodeItemRequest item : request.getItems()) {
            String bankCode = item.getBankCode().trim();
            item.setBankCode(bankCode);
            targetMap.put(composeKey(bankCode, item.getSupportType()), item);
        }

        // 2) Load existing DB rows once.
        List<PaymentChannelBankDto> dbRows = paymentChannelBankMapper.listByPaymentCurrency(
                request.getPaymentId(), currencyCode);
        Map<String, PaymentChannelBankDto> dbMap = new HashMap<>();
        for (PaymentChannelBankDto row : dbRows) {
            dbMap.put(composeKey(row.getBankCode(), row.getSupportType()), row);
        }

        // 3) Split into insert/update/delete sets by diff.
        List<PaymentChannelBankDto> toInsert = new ArrayList<>();
        List<PaymentChannelBankDto> toUpdate = new ArrayList<>();

        for (Map.Entry<String, PaymentBankCodeItemRequest> entry : targetMap.entrySet()) {
            PaymentBankCodeItemRequest target = entry.getValue();
            PaymentChannelBankDto current = dbMap.remove(entry.getKey());
            if (current == null) {
                PaymentChannelBankDto dto = new PaymentChannelBankDto();
                dto.setPaymentId(request.getPaymentId());
                dto.setBankCode(target.getBankCode());
                dto.setCurrency(currencyCode);
                dto.setSupportType(target.getSupportType());
                dto.setStatus(target.getStatus());
                dto.setCreateTime(now);
                dto.setUpdateTime(now);
                toInsert.add(dto);
                continue;
            }

            // targetMap and dbMap both contain this key: overwrite DB by target status.
            PaymentChannelBankDto updateDto = new PaymentChannelBankDto();
            updateDto.setBankCode(target.getBankCode());
            updateDto.setSupportType(target.getSupportType());
            updateDto.setStatus(target.getStatus() == null ? 0 : target.getStatus());
            toUpdate.add(updateDto);
        }

        List<PaymentChannelBankDto> toDelete = new ArrayList<>();
        for (PaymentChannelBankDto value : dbMap.values()) {
            PaymentChannelBankDto keyDto = new PaymentChannelBankDto();
            keyDto.setBankCode(value.getBankCode());
            keyDto.setSupportType(value.getSupportType());
            toDelete.add(keyDto);
        }

        // 4) Apply DB changes in batches.
        int insertCount = 0;
        int updateCount = 0;
        int deleteCount = 0;

        if (!toDelete.isEmpty()) {
            deleteCount = paymentChannelBankMapper.batchDeleteByKeys(
                    request.getPaymentId(), currencyCode, toDelete);
        }
        if (!toUpdate.isEmpty()) {
            updateCount += paymentChannelBankMapper.batchUpdateStatusByKeys(
                    request.getPaymentId(), currencyCode, now, toUpdate);
        }
        if (!toInsert.isEmpty()) {
            insertCount = paymentChannelBankMapper.batchInsert(toInsert);
        }

        writeDiffOperateLogs(request, currencyCode, targetMap.size(), insertCount, updateCount, deleteCount,
                toInsert, toUpdate, toDelete);

        log.info("updatePaymentBankCodes done, paymentId={}, currencyCode={}, insertCount={}, updateCount={}, deleteCount={}, itemCount={}",
                request.getPaymentId(), currencyCode, insertCount, updateCount, deleteCount, targetMap.size());
        return CommonResponse.success("ok");
    }

    /**
     * Build unique key by bankCode + supportType.
     */
    private String composeKey(String bankCode, Integer supportType) {
        return bankCode + "#" + supportType;
    }

    /**
     * Write operate logs by diff type (insert/update/delete) after DB batch operations.
     */
    private void writeDiffOperateLogs(PaymentBankCodeUpdateRequest request,
                                      String currencyCode,
                                      int requestItemCount,
                                      int insertCount,
                                      int updateCount,
                                      int deleteCount,
                                      List<PaymentChannelBankDto> toInsert,
                                      List<PaymentChannelBankDto> toUpdate,
                                      List<PaymentChannelBankDto> toDelete) {
        if (toInsert != null && !toInsert.isEmpty()) {
            operateLogService.write(
                    OperateInterfaceEnum.UPDATE_PAYMENT_BANK_CODES,
                    request.getUserId(),
                    buildDiffLogPayload(request.getPaymentId(), currencyCode, "INSERT",
                            requestItemCount, insertCount, updateCount, deleteCount, toInsert));
        }
        if (toUpdate != null && !toUpdate.isEmpty()) {
            operateLogService.write(
                    OperateInterfaceEnum.UPDATE_PAYMENT_BANK_CODES,
                    request.getUserId(),
                    buildDiffLogPayload(request.getPaymentId(), currencyCode, "UPDATE",
                            requestItemCount, insertCount, updateCount, deleteCount, toUpdate));
        }
        if (toDelete != null && !toDelete.isEmpty()) {
            operateLogService.write(
                    OperateInterfaceEnum.UPDATE_PAYMENT_BANK_CODES,
                    request.getUserId(),
                    buildDiffLogPayload(request.getPaymentId(), currencyCode, "DELETE",
                            requestItemCount, insertCount, updateCount, deleteCount, toDelete));
        }
    }

    /**
     * Build one diff payload for operate log storage.
     */
    private Map<String, Object> buildDiffLogPayload(Long paymentId,
                                                    String currencyCode,
                                                    String diffType,
                                                    int requestItemCount,
                                                    int insertCount,
                                                    int updateCount,
                                                    int deleteCount,
                                                    List<PaymentChannelBankDto> items) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", paymentId);
        payload.put("currencyCode", currencyCode);
        payload.put("diffType", diffType);
        payload.put("requestItemCount", requestItemCount);
        payload.put("insertCount", insertCount);
        payload.put("updateCount", updateCount);
        payload.put("deleteCount", deleteCount);
        payload.put("items", items);
        return payload;
    }

    @Override
    /**
     * Sync bank code dictionary rows from excel source.
     * <p>
     * Rule:
     * 1) row id exists in DB -> update
     * 2) row id not exists -> insert
     * 3) invalid rows are skipped and returned in response
     */
    public CommonResponse syncBankCodesFromRows(
            List<BankCodeSyncExcelRow> rows,
            String source,
            String userId,
            String userName) {
        List<String> invalidRows = new ArrayList<>();
        int insertedCount = 0;
        int updatedCount = 0;
        int totalRows = rows == null ? 0 : rows.size();
        long now = System.currentTimeMillis() / 1000;

        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                BankCodeSyncExcelRow row = rows.get(i);
                String invalidReason = validateSyncRow(row);
                if (invalidReason != null) {
                    invalidRows.add("row " + (i + 2) + ": " + invalidReason);
                    continue;
                }

                BankCodeDictDto dto = buildSyncDto(row, now);
                BankCodeDictDto db = bankCodeDictMapper.findById(dto.getId());
                if (db == null) {
                    insertedCount += bankCodeDictMapper.insert(dto);
                } else {
                    updatedCount += bankCodeDictMapper.updateById(dto);
                }
            }
        }

        log.info("syncBankCodesFromRows done, source={}, totalRows={}, insertedCount={}, updatedCount={}, invalidCount={}",
                source, totalRows, insertedCount, updatedCount, invalidRows.size());
        return CommonResponse.success(new BankCodeSyncResponse(
                source,
                totalRows,
                insertedCount,
                updatedCount,
                invalidRows.size(),
                invalidRows
        ));
    }

    /**
     * Validate one bank-code excel row.
     */
    private String validateSyncRow(BankCodeSyncExcelRow row) {
        if (row == null) {
            return "empty row";
        }
        if (row.getId() == null || row.getId().trim().isEmpty()) {
            return "id is empty";
        }
        try {
            Long.parseLong(row.getId().trim());
        } catch (Exception e) {
            return "id is invalid";
        }
        if (row.getBankName() == null || row.getBankName().trim().isEmpty()) {
            return "bankName is empty";
        }
        if (row.getBankCode() == null || row.getBankCode().trim().isEmpty()) {
            return "bankCode is empty";
        }
        if (row.getCurrencyCode() == null || row.getCurrencyCode().trim().isEmpty()) {
            return "currencyCode is empty";
        }
        return null;
    }

    /**
     * Convert excel row to DB dto.
     */
    private BankCodeDictDto buildSyncDto(BankCodeSyncExcelRow row, long now) {
        BankCodeDictDto dto = new BankCodeDictDto();
        dto.setId(Long.parseLong(row.getId().trim()));
        dto.setBankName(trim(row.getBankName()));
        dto.setBankCode(trim(row.getBankCode()));
        dto.setCountry(trim(row.getCountry()));
        dto.setCurrencyName(trim(row.getCurrencyName()));
        dto.setCurrencyCode(normalizeCurrencyCode(row.getCurrencyCode()));
        dto.setCreateTime(now);
        dto.setUpdateTime(now);
        return dto;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeCurrencyCode(String currencyCode) {
        return currencyCode == null ? null : currencyCode.trim().toUpperCase(Locale.ROOT);
    }
}
