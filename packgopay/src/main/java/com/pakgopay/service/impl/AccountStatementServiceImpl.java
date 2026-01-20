package com.pakgopay.service.impl;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.account.AccountStatementEntity;
import com.pakgopay.data.reqeust.account.AccountStatementAddRequest;
import com.pakgopay.data.reqeust.account.AccountStatementQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.data.response.account.AccountStatementsResponse;
import com.pakgopay.mapper.AccountStatementsMapper;
import com.pakgopay.mapper.dto.AccountStatementsDto;
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.common.AccountStatementService;
import com.pakgopay.util.CommontUtil;
import com.pakgopay.util.PatchBuilderUtil;
import com.pakgopay.util.TransactionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AccountStatementServiceImpl implements AccountStatementService {

    @Autowired
    private AccountStatementsMapper accountStatementsMapper;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private TransactionUtil transactionUtil;

    @Override
    public CommonResponse queryMerchantRecharge(AccountStatementQueryRequest accountStatementQueryRequest) {
        log.info("queryMerchantRecharge start");
        AccountStatementsResponse response = queryMerchantRechargeData(
                accountStatementQueryRequest);
        log.info("queryMerchantRecharge end");
        return CommonResponse.success(response);
    }

    private AccountStatementsResponse queryMerchantRechargeData(AccountStatementQueryRequest accountStatementQueryRequest) {
        log.info("queryMerchantRechargeData start");
        AccountStatementEntity entity = new AccountStatementEntity();
        entity.setId(accountStatementQueryRequest.getId());
        entity.setUserId(accountStatementQueryRequest.getUserId());
        entity.setOrderType(accountStatementQueryRequest.getOrderType());
        entity.setCurrency(accountStatementQueryRequest.getCurrency());
        entity.setStartTime(accountStatementQueryRequest.getStartTime());
        entity.setEndTime(accountStatementQueryRequest.getEndTime());
        entity.setPageSize(accountStatementQueryRequest.getPageSize());
        entity.setPageNo(accountStatementQueryRequest.getPageNo());

        AccountStatementsResponse response = new AccountStatementsResponse();
        try {
            Integer totalNumber = accountStatementsMapper.countByQuery(entity);
            List<AccountStatementsDto> accountStatementsDtoList = accountStatementsMapper.pageByQuery(entity);

            response.setAccountStatementsDtoList(accountStatementsDtoList);
            response.setTotalNumber(totalNumber);
        } catch (Exception e) {
            log.error("accountStatementsMapper pageByQuery failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        response.setPageNo(entity.getPageNo());
        response.setPageSize(entity.getPageSize());
        log.info("queryMerchantRechargeData end");
        return response;
    }

    @Override
    public CommonResponse addMerchantRecharge(AccountStatementAddRequest accountStatementAddRequest) {
        log.info("addMerchantRecharge start");
        AccountStatementsDto accountStatementsDto = generateAccountStatementForAdd(accountStatementAddRequest);

        transactionUtil.runInTransaction(() -> {
            accountStatementsMapper.insert(accountStatementsDto);
            // recharge
            if (accountStatementsDto.getOrderType() == 1) {
                balanceService.rechargeAmount(
                        accountStatementsDto.getUserId(),
                        accountStatementsDto.getCurrency(),
                        accountStatementsDto.getAmount());
            }
            // withdrawal
            if (accountStatementsDto.getOrderType() == 2) {
                balanceService.withdrawAmount(
                        accountStatementsDto.getUserId(),
                        accountStatementsDto.getCurrency(),
                        accountStatementsDto.getAmount(),
                        0);
            }
            // adjust
            if (accountStatementsDto.getOrderType() == 1) {
                balanceService.adjustAmount(
                        accountStatementsDto.getUserId(),
                        accountStatementsDto.getCurrency(),
                        accountStatementsDto.getAmount());
            }
        });

        log.info("addMerchantRecharge end");
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private AccountStatementsDto generateAccountStatementForAdd(AccountStatementAddRequest req) {
        AccountStatementsDto dto = new AccountStatementsDto();
        long now = System.currentTimeMillis() / 1000;

        PatchBuilderUtil<AccountStatementAddRequest, AccountStatementsDto> b = PatchBuilderUtil.from(req).to(dto)
                .reqStr("merchantAgentId", req::getMerchantAgentId, dto::setUserId)
                .reqStr("merchantAgentName", req::getMerchantAgentName, dto::setName)
                .reqStr("currency", req::getCurrency, dto::setCurrency)
                .reqObj("amount", req::getAmount, dto::setAmount)
                .reqObj("orderType", req::getOrderType, dto::setOrderType)
                .ifTrue(2 == req.getOrderType())
                .reqStr("walletAddr", req::getWalletAddr, dto::setWalletAddr)
                .endSkip()
                .str(req::getRemark, dto::setRemark);

        // 4) meta
        dto.setCreateTime(now);
        dto.setUpdateTime(now);
        dto.setCreateBy(req.getUserName());
        dto.setUpdateBy(req.getUserName());

        if (2 == req.getOrderType()) {
            dto.setStatus(0);
        } else {
            dto.setStatus(1);
        }

        Map<String, Map<String, BigDecimal>> cardInfo = balanceService.getBalanceInfos(new ArrayList<>() {{
            add(dto.getUserId());
        }});
        String currency = dto.getCurrency();
        Map<String, BigDecimal> balanceInfo = cardInfo.get(currency);

        BigDecimal availableBalanceBefore = BigDecimal.ZERO;
        BigDecimal frozenBalanceBefore = BigDecimal.ZERO;
        BigDecimal totalBalanceBefore = BigDecimal.ZERO;
        if (balanceInfo != null && !balanceInfo.isEmpty()) {
            balanceService.createBalanceRecord(dto.getUserId(),dto.getCurrency());
            availableBalanceBefore = balanceInfo.getOrDefault("available", BigDecimal.ZERO);
            frozenBalanceBefore = balanceInfo.getOrDefault("frozen", BigDecimal.ZERO);
            totalBalanceBefore = balanceInfo.getOrDefault("total", BigDecimal.ZERO);
        }

        dto.setFrozenBalanceBefore(frozenBalanceBefore);
        dto.setFrozenBalanceAfter(CommontUtil.safeAdd(frozenBalanceBefore, req.getAmount()));
        dto.setAvailableBalanceBefore(availableBalanceBefore);
        dto.setAvailableBalanceAfter(CommontUtil.safeAdd(availableBalanceBefore, req.getAmount()));
        dto.setTotalBalanceBefore(totalBalanceBefore);
        dto.setTotalBalanceAfter(CommontUtil.safeAdd(totalBalanceBefore, req.getAmount()));

        return b.build();
    }
}


