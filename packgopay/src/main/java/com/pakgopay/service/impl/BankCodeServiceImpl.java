package com.pakgopay.service.impl;

import com.pakgopay.data.reqeust.bankCode.BankCodeQueryRequest;
import com.pakgopay.data.reqeust.bankCode.PaymentBankCodeItemRequest;
import com.pakgopay.data.reqeust.bankCode.PaymentBankCodeUpdateRequest;
import com.pakgopay.data.reqeust.bankCode.PaymentBankCodeQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.bankCode.BankCodeQueryResponse;
import com.pakgopay.data.response.bankCode.PaymentBankCodeQueryResponse;
import com.pakgopay.mapper.BankCodeDictMapper;
import com.pakgopay.mapper.PaymentChannelBankMapper;
import com.pakgopay.mapper.dto.BankCodeDictDto;
import com.pakgopay.mapper.dto.PaymentBankCodeDto;
import com.pakgopay.mapper.dto.PaymentChannelBankDto;
import com.pakgopay.service.BankCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BankCodeServiceImpl implements BankCodeService {

    @Autowired
    private BankCodeDictMapper bankCodeDictMapper;

    @Autowired
    private PaymentChannelBankMapper paymentChannelBankMapper;

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
     * Query all bank codes under a currency and mark selected/enabled state for one payment channel.
     */
    public CommonResponse queryPaymentBankCode(PaymentBankCodeQueryRequest request) {
        Integer totalNumber = bankCodeDictMapper.countByPaymentCurrency(request);
        List<PaymentBankCodeDto> list = bankCodeDictMapper.pageByPaymentCurrency(request);

        PaymentBankCodeQueryResponse response = new PaymentBankCodeQueryResponse();
        response.setTotalNumber(totalNumber);
        response.setPageNo(request.getPageNo());
        response.setPageSize(request.getPageSize());
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
}
