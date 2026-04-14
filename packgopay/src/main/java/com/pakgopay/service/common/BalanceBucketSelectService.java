package com.pakgopay.service.common;

import com.pakgopay.mapper.BalanceBucketMapper;
import com.pakgopay.mapper.dto.BalanceBucketDeltaDto;
import com.pakgopay.mapper.dto.BalanceDto;
import com.pakgopay.thirdUtil.RedisUtil;
import com.pakgopay.util.CommonUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BalanceBucketSelectService {

    private static final String CACHE_KEY_PREFIX = "balance:bucket:view:";
    private static final int CACHE_TTL_SECONDS = 24 * 60 * 60;
    private static final BigDecimal LARGE_CREDIT_SPLIT_THRESHOLD = new BigDecimal("1000");
    private static final int LARGE_CREDIT_SPLIT_BUCKETS = 4;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private BalanceBucketMapper balanceBucketMapper;

    public Integer selectBucketNo(String userId, String currency, BigDecimal amount, BucketSelectAction action) {
        if (action == BucketSelectAction.CREDIT || action == BucketSelectAction.ADJUST_INCREASE) {
            ensureBucketsForCredit(userId, currency);
        }
        List<BalanceDto> buckets = getBucketView(userId, currency);
        if (buckets == null || buckets.isEmpty()) {
            return null;
        }
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        return switch (action) {
            case CREDIT, ADJUST_INCREASE -> buckets.stream()
                    .min(Comparator.comparing(bucket -> amountDefaultValue(bucket.getTotalBalance())))
                    .map(BalanceDto::getBucketNo)
                    .orElse(null);
            case FREEZE, WITHDRAW_FREEZE, ADJUST_DECREASE -> selectDistributedBucketNo(
                    buckets,
                    safeAmount,
                    action,
                    CommonUtil.resolveBalanceRouteKey());
            case RELEASE_FROZEN, WITHDRAW_CONFIRM, WITHDRAW_CANCEL, CONFIRM_PAYOUT -> buckets.stream()
                    .filter(bucket -> amountDefaultValue(bucket.getFrozenBalance()).compareTo(safeAmount) >= 0)
                    .min(Comparator.comparing(bucket -> amountDefaultValue(bucket.getFrozenBalance())))
                    .map(BalanceDto::getBucketNo)
                    .orElse(null);
        };
    }

    public boolean shouldSplitLargeCredit(BigDecimal amount) {
        return amount != null && amount.compareTo(LARGE_CREDIT_SPLIT_THRESHOLD) >= 0;
    }

    public void ensureBucketsForCredit(String userId, String currency) {
        if (userId == null || userId.isBlank() || currency == null || currency.isBlank()) {
            return;
        }
        String cacheKey = buildCacheKey(userId, currency);
        try {
            BucketViewCache cached = redisUtil.getObjectValue(cacheKey, BucketViewCache.class);
            if (cached != null && cached.getBuckets() != null && !cached.getBuckets().isEmpty()) {
                return;
            }
        } catch (Exception e) {
            log.warn("load bucket view cache failed before ensure, userId={}, currency={}, message={}",
                    userId, currency, e.getMessage());
        }

        List<BalanceDto> buckets = balanceBucketMapper.listBuckets(userId, currency);
        if (buckets != null && !buckets.isEmpty()) {
            saveBucketView(userId, currency, buckets);
            return;
        }

        long now = System.currentTimeMillis() / 1000;
        int inserted = balanceBucketMapper.insertZeroBuckets(userId, currency, now, now);
        List<BalanceDto> createdBuckets = balanceBucketMapper.listBuckets(userId, currency);
        if (createdBuckets != null && !createdBuckets.isEmpty()) {
            saveBucketView(userId, currency, createdBuckets);
            log.info("ensure balance buckets for credit done, userId={}, currency={}, inserted={}",
                    userId, currency, inserted);
        }
    }

    public List<BalanceBucketDeltaDto> buildLargeCreditDeltas(String userId, String currency, BigDecimal amount) {
        List<BalanceDto> buckets = getBucketView(userId, currency);
        if (buckets == null || buckets.isEmpty() || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        List<BalanceDto> targets = buckets.stream()
                .sorted(Comparator.comparing(bucket -> amountDefaultValue(bucket.getTotalBalance())))
                .limit(Math.min(LARGE_CREDIT_SPLIT_BUCKETS, buckets.size()))
                .toList();
        if (targets.isEmpty()) {
            return List.of();
        }
        BigDecimal divisor = BigDecimal.valueOf(targets.size());
        BigDecimal baseShare = amount.divideToIntegralValue(divisor);
        BigDecimal remainder = amount.subtract(baseShare.multiply(divisor));
        List<BalanceBucketDeltaDto> deltas = new ArrayList<>(targets.size());
        for (int i = 0; i < targets.size(); i++) {
            BalanceDto bucket = targets.get(i);
            BigDecimal share = i == 0 ? baseShare.add(remainder) : baseShare;
            if (share.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BalanceBucketDeltaDto delta = new BalanceBucketDeltaDto();
            delta.setBucketNo(bucket.getBucketNo());
            delta.setAvailableDelta(share);
            delta.setFrozenDelta(BigDecimal.ZERO);
            delta.setTotalDelta(share);
            delta.setWithdrawDelta(BigDecimal.ZERO);
            deltas.add(delta);
        }
        return deltas;
    }

    public List<Integer> selectLowestTotalBalanceBucketNos(String userId, String currency, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        ensureBucketsForCredit(userId, currency);
        List<BalanceDto> buckets = getBucketView(userId, currency);
        if (buckets == null || buckets.isEmpty()) {
            return List.of();
        }
        return buckets.stream()
                .sorted(Comparator.comparing(bucket -> amountDefaultValue(bucket.getTotalBalance())))
                .limit(limit)
                .map(BalanceDto::getBucketNo)
                .filter(bucketNo -> bucketNo != null)
                .toList();
    }

    public void applyBucketDeltas(String userId, String currency, List<BalanceBucketDeltaDto> deltas) {
        if (deltas == null || deltas.isEmpty()) {
            return;
        }
        List<BalanceDto> buckets = getBucketView(userId, currency);
        if (buckets == null || buckets.isEmpty()) {
            refreshBucketView(userId, currency);
            return;
        }
        for (BalanceBucketDeltaDto delta : deltas) {
            if (delta == null || delta.getBucketNo() == null) {
                continue;
            }
            BalanceDto target = buckets.stream()
                    .filter(bucket -> delta.getBucketNo().equals(bucket.getBucketNo()))
                    .findFirst()
                    .orElse(null);
            if (target == null) {
                refreshBucketView(userId, currency);
                return;
            }
            target.setAvailableBalance(amountDefaultValue(target.getAvailableBalance()).add(amountDefaultValue(delta.getAvailableDelta())));
            target.setFrozenBalance(amountDefaultValue(target.getFrozenBalance()).add(amountDefaultValue(delta.getFrozenDelta())));
            target.setTotalBalance(amountDefaultValue(target.getTotalBalance()).add(amountDefaultValue(delta.getTotalDelta())));
            target.setWithdrawAmount(amountDefaultValue(target.getWithdrawAmount()).add(amountDefaultValue(delta.getWithdrawDelta())));
        }
        saveBucketView(userId, currency, buckets);
    }

    public void refreshBucketView(String userId, String currency) {
        try {
            List<BalanceDto> buckets = balanceBucketMapper.listBuckets(userId, currency);
            if (buckets == null || buckets.isEmpty()) {
                redisUtil.remove(buildCacheKey(userId, currency));
                return;
            }
            saveBucketView(userId, currency, buckets);
        } catch (Exception e) {
            log.warn("refresh bucket view failed, userId={}, currency={}, message={}",
                    userId, currency, e.getMessage());
        }
    }

    private List<BalanceDto> getBucketView(String userId, String currency) {
        String cacheKey = buildCacheKey(userId, currency);
        try {
            log.info("balance bucket view load start, userId={}, currency={}", userId, currency);
            BucketViewCache cached = redisUtil.getObjectValue(cacheKey, BucketViewCache.class);
            if (cached != null && cached.getBuckets() != null && !cached.getBuckets().isEmpty()) {
                log.info("balance bucket view cache hit, userId={}, currency={}, bucketCount={}",
                        userId, currency, cached.getBuckets().size());
                return cached.getBuckets();
            }
            log.info("balance bucket view cache miss, userId={}, currency={}", userId, currency);
        } catch (Exception e) {
            log.warn("load bucket view cache failed, userId={}, currency={}, message={}",
                    userId, currency, e.getMessage());
        }
        log.info("balance bucket view db load start, userId={}, currency={}", userId, currency);
        List<BalanceDto> buckets = balanceBucketMapper.listBuckets(userId, currency);
        if (buckets == null || buckets.isEmpty()) {
            log.info("balance bucket view db load empty, userId={}, currency={}", userId, currency);
            return buckets;
        }
        saveBucketView(userId, currency, buckets);
        log.info("balance bucket view db load done, userId={}, currency={}, bucketCount={}",
                userId, currency, buckets.size());
        return buckets;
    }

    private void saveBucketView(String userId, String currency, List<BalanceDto> buckets) {
        try {
            redisUtil.setObjectWithSecondExpire(buildCacheKey(userId, currency),
                    new BucketViewCache(new ArrayList<>(buckets)),
                    CACHE_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("save bucket view cache failed, userId={}, currency={}, message={}",
                    userId, currency, e.getMessage());
        }
    }

    private String buildCacheKey(String userId, String currency) {
        return CACHE_KEY_PREFIX + userId + ":" + currency;
    }

    private BigDecimal amountDefaultValue(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Integer selectDistributedBucketNo(
            List<BalanceDto> buckets,
            BigDecimal amount,
            BucketSelectAction action,
            String routeKey) {
        BigDecimal requiredAmount = amount == null ? BigDecimal.ZERO : amount.abs();
        List<BalanceDto> eligible = buckets.stream()
                .filter(bucket -> amountDefaultValue(bucket.getAvailableBalance()).compareTo(requiredAmount) >= 0)
                .sorted(Comparator.comparing(BalanceDto::getBucketNo))
                .collect(Collectors.toList());
        if (eligible.isEmpty()) {
            return null;
        }
        if (routeKey == null || routeKey.isBlank()) {
            return eligible.stream()
                    .max(Comparator.comparing(bucket -> amountDefaultValue(bucket.getAvailableBalance())))
                    .map(BalanceDto::getBucketNo)
                    .orElse(null);
        }
        int startIndex = CommonUtil.resolveBalanceBucketNo(routeKey + ":" + action.name(), eligible.size());
        for (int i = 0; i < eligible.size(); i++) {
            BalanceDto candidate = eligible.get((startIndex + i) % eligible.size());
            if (amountDefaultValue(candidate.getAvailableBalance()).compareTo(requiredAmount) >= 0) {
                return candidate.getBucketNo();
            }
        }
        return null;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BucketViewCache {
        private List<BalanceDto> buckets;
    }

    public enum BucketSelectAction {
        CREDIT,
        FREEZE,
        RELEASE_FROZEN,
        ADJUST_INCREASE,
        ADJUST_DECREASE,
        WITHDRAW_FREEZE,
        WITHDRAW_CONFIRM,
        WITHDRAW_CANCEL,
        CONFIRM_PAYOUT
    }
}
