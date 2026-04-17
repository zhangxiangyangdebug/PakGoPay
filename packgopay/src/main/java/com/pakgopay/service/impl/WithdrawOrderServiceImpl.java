package com.pakgopay.service.impl;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.data.entity.account.WithdrawOrderEntity;
import com.pakgopay.data.reqeust.account.WithdrawOrderAuditRequest;
import com.pakgopay.data.reqeust.account.WithdrawOrderCreateRequest;
import com.pakgopay.data.reqeust.account.WithdrawOrderQueryRequest;
import com.pakgopay.data.response.CommonResponse;
import com.pakgopay.mapper.AccountStatementsMapper;
import com.pakgopay.mapper.WithdrawOrderMapper;
import com.pakgopay.mapper.dto.AccountStatementsDto;
import com.pakgopay.mapper.dto.WithdrawOrderDto;
import com.pakgopay.service.BalanceService;
import com.pakgopay.service.WithdrawOrderService;
import com.pakgopay.service.common.OrderInterventionTelegramNotifier;
import com.pakgopay.util.CommonUtil;
import com.pakgopay.util.PatchBuilderUtil;
import com.pakgopay.util.SnowflakeIdGenerator;
import com.pakgopay.util.SnowflakeIdService;
import com.pakgopay.util.TransactionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class WithdrawOrderServiceImpl extends AbstractStatementSnapshotService implements WithdrawOrderService {

    /**
     * 0.pending 1.rejected 2.success
     */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_REJECTED = 1;
    private static final int STATUS_SUCCESS = 2;

    /**
     * 31.withdraw_freeze 32.withdraw_release 33.withdraw_confirm
     */
    private static final int ORDER_TYPE_WITHDRAW_FREEZE = 31;
    private static final int ORDER_TYPE_WITHDRAW_RELEASE = 32;
    private static final int ORDER_TYPE_WITHDRAW_CONFIRM = 33;

    private static final List<Integer> WITHDRAW_STATEMENT_ORDER_TYPES = List.of(
            ORDER_TYPE_WITHDRAW_FREEZE,
            ORDER_TYPE_WITHDRAW_RELEASE,
            ORDER_TYPE_WITHDRAW_CONFIRM
    );

    @Autowired
    private WithdrawOrderMapper withdrawOrderMapper;

    @Autowired
    private AccountStatementsMapper accountStatementsMapper;

    @Autowired
    private SnowflakeIdService snowflakeIdService;

    @Autowired
    private TransactionUtil transactionUtil;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private OrderInterventionTelegramNotifier orderInterventionTelegramNotifier;

    /**
     * Query withdraw order page.
     */
    @Override
    public CommonResponse queryWithdrawOrderPage(WithdrawOrderQueryRequest request) {
        log.info("queryWithdrawOrder start");
        Map<String, Object> response = loadWithdrawOrderPage(request);
        log.info("queryWithdrawOrder end");
        return CommonResponse.success(response);
    }

    private Map<String, Object> loadWithdrawOrderPage(WithdrawOrderQueryRequest request) {
        log.info("loadWithdrawOrderPage start");
        WithdrawOrderEntity entity = new WithdrawOrderEntity();
        entity.setWithdrawNo(request.getWithdrawNo());
        entity.setMerchantAgentId(request.getMerchantAgentId());
        entity.setUserRole(request.getUserRole());
        entity.setCurrency(request.getCurrency());
        entity.setStatus(request.getStatus());
        entity.setRequestIp(request.getRequestIp());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setPageSize(request.getPageSize());
        entity.setPageNo(request.getPageNo());

        if (entity.getPageNo() != null && entity.getPageSize() != null
                && entity.getPageNo() > 0 && entity.getPageSize() > 0) {
            entity.setOffset((entity.getPageNo() - 1) * entity.getPageSize());
        }

        Map<String, Object> response = new HashMap<>();
        try {
            Integer totalNumber = withdrawOrderMapper.countByQuery(entity);
            List<WithdrawOrderDto> withdrawOrderDtoList = withdrawOrderMapper.pageByQuery(entity);

            response.put("totalNumber", totalNumber);
            response.put("withdrawOrderDtoList", withdrawOrderDtoList);
            response.put("pageNo", entity.getPageNo());
            response.put("pageSize", entity.getPageSize());
        } catch (Exception e) {
            log.error("withdrawOrderMapper pageByQuery failed, message {}", e.getMessage());
            throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
        }

        log.info("loadWithdrawOrderPage end");
        return response;
    }

    /**
     * Create one withdraw order and one freeze statement.
     */
    @Override
    public CommonResponse createWithdrawOrder(WithdrawOrderCreateRequest request) {
        log.info("createWithdrawOrder start");

        WithdrawOrderEntity order = generateWithdrawOrderForCreate(request);
        AccountStatementsDto freezeStatement = generateFreezeStatement(order, request);

        transactionUtil.runInTransaction(() -> {
            try {
                int inserted = withdrawOrderMapper.insert(order);
                if (inserted <= 0) {
                    throw new PakGoPayException(ResultCode.FAIL, "create withdraw order failed");
                }

                accountStatementsMapper.insert(freezeStatement);

                CommonUtil.withBalanceLogContext("withdrawOrder.create", freezeStatement.getSerialNo(), () -> {
                    balanceService.applyWithdrawalOperation(
                            freezeStatement.getUserId(),
                            freezeStatement.getCurrency(),
                            freezeStatement.getAmount(),
                            0,
                            null
                    );
                });
                AccountStatementsDto insertedStatement = loadStatementBySerialNo(freezeStatement.getSerialNo());
                enqueuePendingSnapshot(
                        freezeStatement.getUserId(),
                        freezeStatement.getCurrency(),
                        insertedStatement == null ? freezeStatement.getCreateTime() : insertedStatement.getCreateTime()
                );
            } catch (PakGoPayException e) {
                throw e;
            } catch (Exception e) {
                log.error("createWithdrawOrder transaction failed, withdrawNo={}, message={}",
                        order.getWithdrawNo(), e.getMessage());
                throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
            }
        });

        try {
            orderInterventionTelegramNotifier.notifyPendingWithdrawOrder(order.getWithdrawNo(), order.getCreateTime());
        } catch (Exception e) {
            log.warn("send telegram withdraw notify failed, withdrawNo={}, message={}",
                    order.getWithdrawNo(), e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("withdrawNo", order.getWithdrawNo());

        log.info("createWithdrawOrder end, withdrawNo={}", order.getWithdrawNo());
        return CommonResponse.success(result);
    }

    /**
     * Build withdraw order entity for create.
     */
    private WithdrawOrderEntity generateWithdrawOrderForCreate(WithdrawOrderCreateRequest request) {
        String withdrawNo = snowflakeIdService.nextId(CommonConstant.WITHDRAW_ORDER_PREFIX);
        Long epochSecond = SnowflakeIdGenerator.extractEpochSecondFromPrefixedId(withdrawNo);
        long now = epochSecond != null && epochSecond > 0 ? epochSecond : System.currentTimeMillis() / 1000;

        WithdrawOrderEntity entity = new WithdrawOrderEntity();
        PatchBuilderUtil<WithdrawOrderCreateRequest, WithdrawOrderEntity> b = PatchBuilderUtil.from(request).to(entity)
                .reqStr("userId", request::getUserId, entity::setMerchantAgentId)
                .reqObj("userRole", request::getUserRole, entity::setUserRole)
                .reqStr("name", request::getName, entity::setName)
                .reqStr("currency", request::getCurrency, entity::setCurrency)
                .reqObj("amount", request::getAmount, entity::setAmount)
                .str(request::getWalletAddr, entity::setWalletAddr)
                .str(request::getRequestIp, entity::setRequestIp)
                .str(request::getRemark, entity::setRemark);

        entity.setWithdrawNo(withdrawNo);
        entity.setStatus(STATUS_PENDING);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setCreateBy(request.getUserName());
        entity.setUpdateBy(request.getUserName());

        return b.build();
    }

    /**
     * Build withdraw freeze statement.
     */
    private AccountStatementsDto generateFreezeStatement(WithdrawOrderEntity order, WithdrawOrderCreateRequest request) {
        AccountStatementsDto dto = new AccountStatementsDto();
        String serialNo = snowflakeIdService.nextId(CommonConstant.STATEMENT_PREFIX);
        long createTime = resolveCreateTimeFromSerialNo(serialNo);

        dto.setSerialNo(serialNo);
        dto.setTransactionNo(order.getWithdrawNo());
        dto.setOrderType(ORDER_TYPE_WITHDRAW_FREEZE);
        dto.setAmount(order.getAmount());
        dto.setStatus(3);
        dto.setUserId(order.getMerchantAgentId());
        dto.setUserRole(order.getUserRole());
        dto.setName(order.getName());
        dto.setCurrency(order.getCurrency());
        dto.setWalletAddr(order.getWalletAddr());
        dto.setRequestIp(order.getRequestIp());
        dto.setRemark(buildStatementRemark("order:withdraw_freeze", request.getRemark()));
        dto.setCreateBy(request.getUserName());
        dto.setUpdateTime(createTime);
        dto.setUpdateBy(request.getUserName());
        return dto;
    }

    /**
     * Audit one withdraw order.
     * status: 1.rejected 2.success
     */
    @Override
    public CommonResponse auditWithdrawOrder(WithdrawOrderAuditRequest request) {
        log.info("auditWithdrawOrder start, withdrawNo={}", request.getWithdrawNo());

        WithdrawOrderDto dbOrder = withdrawOrderMapper.findByWithdrawNo(request.getWithdrawNo()).orElse(null);
        if (dbOrder == null) {
            log.warn("withdraw order not found, withdrawNo={}", request.getWithdrawNo());
            throw new PakGoPayException(ResultCode.FAIL, "withdraw order not found");
        }

        if (dbOrder.getStatus() == null || dbOrder.getStatus() != STATUS_PENDING) {
            log.warn("withdraw order status invalid, withdrawNo={}, status={}",
                    request.getWithdrawNo(), dbOrder.getStatus());
            throw new PakGoPayException(ResultCode.FAIL, "withdraw order status is invalid");
        }

        validateAuditRequest(request);
        AccountStatementsDto auditStatement = generateAuditStatement(dbOrder, request);

        transactionUtil.runInTransaction(() -> {
            try {
                int updated = withdrawOrderMapper.updateStatusByWithdrawNo(
                        request.getWithdrawNo(),
                        request.getStatus(),
                        request.getFailReason(),
                        request.getRemark(),
                        request.getUserName(),
                        auditStatement.getUpdateTime()
                );
                if (updated <= 0) {
                    throw new PakGoPayException(ResultCode.FAIL, "audit withdraw order failed");
                }

                accountStatementsMapper.insert(auditStatement);

                CommonUtil.withBalanceLogContext("withdrawOrder.audit", auditStatement.getSerialNo(), () -> {
                    balanceService.applyWithdrawalOperation(
                            auditStatement.getUserId(),
                            auditStatement.getCurrency(),
                            auditStatement.getAmount(),
                            request.getStatus() == STATUS_SUCCESS ? 1 : 2,
                            null
                    );
                });
                if (request.getStatus() == STATUS_SUCCESS) {
                    AccountStatementsDto insertedStatement = loadStatementBySerialNo(auditStatement.getSerialNo());
                    enqueuePendingSnapshot(
                            auditStatement.getUserId(),
                            auditStatement.getCurrency(),
                            insertedStatement == null ? auditStatement.getCreateTime() : insertedStatement.getCreateTime()
                    );
                }
            } catch (PakGoPayException e) {
                throw e;
            } catch (Exception e) {
                log.error("auditWithdrawOrder transaction failed, withdrawNo={}, message={}",
                        request.getWithdrawNo(), e.getMessage());
                throw new PakGoPayException(ResultCode.DATA_BASE_ERROR);
            }
        });

        log.info("auditWithdrawOrder end, withdrawNo={}, status={}", request.getWithdrawNo(), request.getStatus());
        return CommonResponse.success(ResultCode.SUCCESS);
    }

    private void validateAuditRequest(WithdrawOrderAuditRequest request) {
        if (request.getStatus() == null
                || (request.getStatus() != STATUS_REJECTED && request.getStatus() != STATUS_SUCCESS)) {
            log.warn("invalid audit status, withdrawNo={}, status={}", request.getWithdrawNo(), request.getStatus());
            throw new PakGoPayException(ResultCode.FAIL, "invalid audit status");
        }

        if (request.getStatus() == STATUS_REJECTED
                && (request.getFailReason() == null || request.getFailReason().isBlank())) {
            log.warn("failReason is empty, withdrawNo={}", request.getWithdrawNo());
            throw new PakGoPayException(ResultCode.FAIL, "failReason is empty");
        }
    }

    /**
     * Build withdraw release / confirm statement for audit.
     */
    private AccountStatementsDto generateAuditStatement(WithdrawOrderDto order, WithdrawOrderAuditRequest request) {
        AccountStatementsDto dto = new AccountStatementsDto();
        String serialNo = snowflakeIdService.nextId(CommonConstant.STATEMENT_PREFIX);
        long createTime = resolveCreateTimeFromSerialNo(serialNo);

        dto.setSerialNo(serialNo);
        dto.setTransactionNo(order.getWithdrawNo());
        dto.setAmount(order.getAmount());
        dto.setUserId(order.getUserId());
        dto.setUserRole(order.getUserRole());
        dto.setName(order.getName());
        dto.setCurrency(order.getCurrency());
        dto.setWalletAddr(order.getWalletAddr());
        dto.setRequestIp(order.getRequestIp());
        dto.setStatus(3);
        dto.setCreateBy(request.getUserName());
        dto.setUpdateTime(createTime);
        dto.setUpdateBy(request.getUserName());

        if (request.getStatus() == STATUS_REJECTED) {
            dto.setOrderType(ORDER_TYPE_WITHDRAW_RELEASE);
            dto.setRemark(buildStatementRemark("order:withdraw_release", request.getRemark()));
        } else {
            dto.setOrderType(ORDER_TYPE_WITHDRAW_CONFIRM);
            dto.setRemark(buildStatementRemark("order:withdraw_confirm", request.getRemark()));
        }

        return dto;
    }

    private String buildStatementRemark(String action, String extraRemark) {
        if (extraRemark == null || extraRemark.isBlank()) {
            return action;
        }
        return action + "|" + extraRemark;
    }
}
