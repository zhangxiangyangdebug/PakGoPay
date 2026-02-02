package com.pakgopay.timer;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.mapper.*;
import com.pakgopay.mapper.dto.*;
import com.pakgopay.service.impl.AgentServiceImpl;
import com.pakgopay.service.impl.MerchantServiceImpl;
import com.pakgopay.util.CommonUtil;
import com.pakgopay.util.PatchBuilderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pakgopay.timer.data.OpsContext;
import com.pakgopay.timer.data.OpsOrderContext;
import com.pakgopay.timer.data.OpsPeriod;
import com.pakgopay.timer.data.OpsRecord;
import com.pakgopay.timer.data.OpsScope;
import com.pakgopay.timer.data.OpsTotals;
import com.pakgopay.timer.data.ReportCurrencyRange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReportTask {

    @Autowired
    private MerchantInfoMapper merchantInfoMapper;

    @Autowired
    private MerchantReportMapper merchantReportMapper;

    @Autowired
    private ChannelReportMapper channelReportMapper;

    @Autowired
    private AgentReportMapper agentReportMapper;

    @Autowired
    private CurrencyReportMapper currencyReportMapper;

    @Autowired
    private PaymentReportMapper paymentReportMapper;

    @Autowired
    private CollectionOrderMapper collectionOrderMapper;

    @Autowired
    private PayOrderMapper payOrderMapper;

    @Autowired
    private ChannelMapper channelMapper;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private AgentInfoMapper agentInfoMapper;

    @Autowired
    private OpsOrderDailyMapper opsOrderDailyMapper;

    @Autowired
    private OpsOrderMonthlyMapper opsOrderMonthlyMapper;

    @Autowired
    private OpsOrderYearlyMapper opsOrderYearlyMapper;

    private final ResolveHelper resolveHelper = new ResolveHelper();
    private final ComputeHelper computeHelper = new ComputeHelper();
    private final FillHelper fillHelper = new FillHelper();
    private final PersistHelper persistHelper = new PersistHelper();
    private final AgentHelper agentHelper = new AgentHelper();
    private final OpsHelper opsHelper = new OpsHelper();

    private List<MerchantInfoDto> merchantInfoCache = Collections.emptyList();
    private List<ChannelDto> channelInfoCache = Collections.emptyList();
    private List<PaymentDto> paymentInfoCache = Collections.emptyList();
    private List<AgentInfoDto> agentInfoCache = Collections.emptyList();
    private Map<Long, ChannelDto> channelByIdCache = Collections.emptyMap();
    private Map<Long, PaymentDto> paymentByIdCache = Collections.emptyMap();
    private Map<String, AgentInfoDto> agentByUserIdCache = Collections.emptyMap();
    private Map<String, MerchantInfoDto> merchantByUserIdCache = Collections.emptyMap();
    private Map<String, Long> dayStartByCurrency = new HashMap<>();
    private Map<String, Long> nextDayStartByCurrency = new HashMap<>();
    private Map<String, LocalDate> baseDateByCurrency = new HashMap<>();
    private long nowEpochSecond = 0L;
    private Map<String, AgentReportDto> agentReportMap = new HashMap<>();
    private final Object reportLock = new Object();

    /**
     * Execute hourly report aggregation.
     */
    public void doHourlyReport() {
        synchronized (reportLock) {
            long startMs = System.currentTimeMillis();
            reportAllInfo();
            long costMs = System.currentTimeMillis() - startMs;
            log.info("doHourlyReport costMs={}", costMs);
        }
    }

    private void reportAllInfo(){
        log.info("reportAllInfo start");
        // Clear in-memory caches to avoid stale data between runs.
        resolveHelper.resetReportCache();
        // Load base datasets (merchants/channels/payments/agents) and enrich merchant snapshot.
        resolveHelper.loadMerchantSnapshot();
        // Cache a single time reference for this run.
        nowEpochSecond = Instant.now().getEpochSecond();
        // Build and persist merchant reports first (used for agent rollups).
        reportMerchant();
        // Persist accumulated agent reports after merchant reports.
        agentHelper.upsertAgentReports();
        // Build and persist ops overview reports (daily/monthly/yearly).
        opsHelper.reportOpsDaily();
        opsHelper.reportOpsMonthly();
        opsHelper.reportOpsYearly();
        // Build remaining report types.
        reportChannel();
        reportPayment();
        reportCurrency();

        log.info("reportAllInfo end, merchantCount={}", merchantInfoCache.size());
    }

    /**
     * Refresh reports by order record time and currency timezone.
     * Uses batch stats loading by currency ranges for report aggregation.
     *
     * @param recordDateEpoch record date epoch seconds
     * @param currency currency code
     */
    public void refreshReportsByEpoch(long recordDateEpoch, String currency) {
        synchronized (reportLock) {
            long startMs = System.currentTimeMillis();
            refreshReportsByDate(recordDateEpoch, currency);
            long costMs = System.currentTimeMillis() - startMs;
            log.info("refreshReportsByEpoch costMs={}", costMs);
        }
    }

    /**
     * Refresh reports for a specified date (daily/monthly/yearly) by currency timezone.
     * Uses batch stats loading by currency ranges for report aggregation.
     *
     * @param recordDateEpoch record date epoch seconds
     * @param currency currency code
     */
    private void refreshReportsByDate(long recordDateEpoch, String currency) {
        if (currency == null || currency.isBlank()) {
            log.warn("refreshReportsByDate skipped, currency is blank");
            return;
        }
        ZoneId zoneId = CommonUtil.resolveZoneIdByCurrency(currency);
        LocalDate targetDate = Instant.ofEpochSecond(recordDateEpoch).atZone(zoneId).toLocalDate();
        log.info("refreshReportsByDate start, recordDateEpoch={}, currency={}, targetDate={}",
                recordDateEpoch, currency, targetDate);
        // Reset caches and reload base data
        resolveHelper.resetReportCache();
        resolveHelper.loadMerchantSnapshot();
        nowEpochSecond = Instant.now().getEpochSecond();

        // Fix base date for this run (by currency timezone)
        Set<String> currencySet = new LinkedHashSet<>();
        currencySet.addAll(resolveHelper.resolveAllCurrencies());
        currencySet.addAll(resolveHelper.resolveMerchantCurrencySet());
        resolveHelper.applyFixedReportDate(targetDate, currencySet);

        // Regenerate reports
        reportMerchant();
        agentHelper.upsertAgentReports();
        opsHelper.reportOpsDaily();
        opsHelper.reportOpsMonthly();
        opsHelper.reportOpsYearly();
        reportChannel();
        reportPayment();
        reportCurrency();

        log.info("refreshReportsByDate end, targetDate={}, merchantCount={}", targetDate, merchantInfoCache.size());
    }

    /**
     * Build and persist merchant reports.
     */
    private void reportMerchant() {
        if (merchantInfoCache.isEmpty()) {
            log.warn("doHourlyReport no merchants found");
            return;
        }
        // Resolve all currencies across merchants and load collection/payout stats.
        Set<String> currencies = resolveHelper.resolveMerchantCurrencySet();
        Map<String, MerchantReportDto> collectionStats = computeHelper.loadStatsByCurrency(
                currencies,
                collectionOrderMapper::listMerchantReportStatsBatch,
                dto -> resolveHelper.buildKey(dto == null ? null : dto.getUserId(), dto == null ? null : dto.getCurrency()));
        Map<String, MerchantReportDto> payoutStats = computeHelper.loadStatsByCurrency(
                currencies,
                payOrderMapper::listMerchantReportStatsBatch,
                dto -> resolveHelper.buildKey(dto == null ? null : dto.getUserId(), dto == null ? null : dto.getCurrency()));

        // Process collection and payout reports for each merchant/currency.
        List<MerchantReportDto> reports = computeHelper.processDualSupportReports(
                merchantInfoCache,
                resolveHelper::resolveMerchantCurrenciesForSupport,
                (merchant, currency, isCollection) -> computeHelper.applyMerchantReport(
                        merchant, currency, isCollection, collectionStats, payoutStats));
        persistHelper.batchUpsertMerchantReports(reports);
        log.info("reportMerchant done, merchantCount={}, currencyCount={}, collectionStats={}, payoutStats={}",
                merchantInfoCache.size(),
                currencies.size(),
                collectionStats.size(),
                payoutStats.size());
    }

    /**
     * Build and persist channel reports.
     */
    private void reportChannel() {
        // Resolve all currencies and load channel stats for collection/payout.
        Set<String> currencies = resolveHelper.resolveAllCurrencies();
        Map<String, ChannelReportDto> collectionStats = computeHelper.loadStatsByCurrency(
                currencies,
                collectionOrderMapper::listChannelReportStatsBatch,
                dto -> resolveHelper.buildKey(dto == null ? null : dto.getChannelId(), dto == null ? null : dto.getCurrency()));
        Map<String, ChannelReportDto> payoutStats = computeHelper.loadStatsByCurrency(
                currencies,
                payOrderMapper::listChannelReportStatsBatch,
                dto -> resolveHelper.buildKey(dto == null ? null : dto.getChannelId(), dto == null ? null : dto.getCurrency()));

        // Process channel reports for collection and payout directions.
        List<ChannelReportDto> reports = computeHelper.processDualSupportReports(
                channelInfoCache,
                resolveHelper::resolveChannelCurrencies,
                (channel, currency, isCollection) -> computeHelper.applyChannelReport(
                        channel, currency, isCollection, collectionStats, payoutStats));
        persistHelper.batchUpsertChannelReports(reports);
        log.info("reportChannel done, channelCount={}, currencyCount={}, collectionStats={}, payoutStats={}",
                channelInfoCache.size(),
                currencies.size(),
                collectionStats.size(),
                payoutStats.size());
    }

    /**
     * Build and persist payment reports.
     */
    private void reportPayment() {
        // Resolve all currencies and load payment stats for collection/payout.
        Set<String> currencies = resolveHelper.resolveAllCurrencies();
        Map<String, PaymentReportDto> collectionStats = computeHelper.loadStatsByCurrency(
                currencies,
                collectionOrderMapper::listPaymentReportStatsBatch,
                dto -> resolveHelper.buildKey(dto == null ? null : dto.getPaymentId(), dto == null ? null : dto.getCurrency()));
        Map<String, PaymentReportDto> payoutStats = computeHelper.loadStatsByCurrency(
                currencies,
                payOrderMapper::listPaymentReportStatsBatch,
                dto -> resolveHelper.buildKey(dto == null ? null : dto.getPaymentId(), dto == null ? null : dto.getCurrency()));

        // Process payment reports for collection and payout directions.
        List<PaymentReportDto> reports = computeHelper.processDualSupportReports(
                paymentInfoCache,
                resolveHelper::resolvePaymentCurrencies,
                (payment, currency, isCollection) -> computeHelper.applyPaymentReport(
                        payment, currency, isCollection, collectionStats, payoutStats));
        persistHelper.batchUpsertPaymentReports(reports);
        log.info("reportPayment done, paymentCount={}, currencyCount={}, collectionStats={}, payoutStats={}",
                paymentInfoCache.size(),
                currencies.size(),
                collectionStats.size(),
                payoutStats.size());
    }

    /**
     * Build and persist currency reports.
     */
    private void reportCurrency() {
        // Resolve all currencies and load currency stats for collection/payout.
        Set<String> currencies = resolveHelper.resolveAllCurrencies();
        Map<String, CurrencyReportDto> collectionStats = computeHelper.loadStatsByCurrency(
                currencies,
                collectionOrderMapper::listCurrencyReportStatsBatch,
                dto -> dto == null ? null : dto.getCurrency());
        Map<String, CurrencyReportDto> payoutStats = computeHelper.loadStatsByCurrency(
                currencies,
                payOrderMapper::listCurrencyReportStatsBatch,
                dto -> dto == null ? null : dto.getCurrency());

        // Process currency reports for collection and payout directions.
        List<CurrencyReportDto> reports = computeHelper.processDualSupportReports(
                new ArrayList<>(currencies),
                (currency, isCollection) -> Collections.singleton(currency),
                (currency, reportCurrency, isCollection) -> computeHelper.applyCurrencyReport(
                        reportCurrency, isCollection, collectionStats, payoutStats));
        persistHelper.batchUpsertCurrencyReports(reports);
        log.info("reportCurrency done, currencyCount={}, collectionStats={}, payoutStats={}",
                currencies.size(),
                collectionStats.size(),
                payoutStats.size());
    }
    @FunctionalInterface
    private interface ReportStatsLoader<T> {
        /**
         * Load report statistics by currency and time range.
         *
         * @param currency currency code
         * @param startTime start time
         * @param endTime end time
         * @param successStatus success status code
         * @return report list
         */
        List<T> load(String currency, long startTime, long endTime, String successStatus);
    }

    @FunctionalInterface
    private interface ReportStatsBatchLoader<T> {
        /**
         * Load report statistics by currency and time range.
         *
         * @param ranges currency ranges
         * @param successStatus success status code
         * @return report list
         */
        List<T> load(List<ReportCurrencyRange> ranges, String successStatus);
    }

    @FunctionalInterface
    private interface SupportCurrencyResolver<E> {
        /**
         * Resolve supported currencies for an entity.
         *
         * @param entity entity instance
         * @param isCollection true for collection, false for payout
         * @return currency set
         */
        Set<String> resolve(E entity, boolean isCollection);
    }

    @FunctionalInterface
    private interface SupportReportApplier<E, R> {
        /**
         * Apply report generation for an entity and currency.
         *
         * @param entity entity instance
         * @param currency currency code
         * @param isCollection true for collection, false for payout
         * @return report dto
         */
        R apply(E entity, String currency, boolean isCollection);
    }

    private final class ComputeHelper {
        /**
         * Apply merchant report aggregation and persistence.
         *
         * @param merchant merchant info
         * @param currency currency code
         * @param isCollection true for collection, false for payout
         * @param collectionStats collection stats map
         * @param payoutStats payout stats map
         */
        private MerchantReportDto applyMerchantReport(MerchantInfoDto merchant,
                                                      String currency,
                                                      boolean isCollection,
                                                      Map<String, MerchantReportDto> collectionStats,
                                                      Map<String, MerchantReportDto> payoutStats) {
            if (merchant == null || merchant.getUserId() == null) {
                return null;
            }
            long dayStart = resolveHelper.resolveDayStart(currency);
            int orderType = isCollection ? CommonConstant.SUPPORT_TYPE_COLLECTION : CommonConstant.SUPPORT_TYPE_PAY;
            Map<String, MerchantReportDto> stats = isCollection ? collectionStats : payoutStats;
            MerchantReportDto report = stats.get(merchant.getUserId() + "|" + currency);
            MerchantReportDto dto = fillHelper.fillReportDefaults(report, merchant, currency, orderType, dayStart);
            agentHelper.accumulateAgentReport(merchant, dto, currency, orderType, dayStart);
            return dto;
        }

        /**
         * Apply channel report aggregation and persistence.
         *
         * @param channel channel info
         * @param currency currency code
         * @param isCollection true for collection, false for payout
         * @param collectionStats collection stats map
         * @param payoutStats payout stats map
         */
        private ChannelReportDto applyChannelReport(ChannelDto channel,
                                                    String currency,
                                                    boolean isCollection,
                                                    Map<String, ChannelReportDto> collectionStats,
                                                    Map<String, ChannelReportDto> payoutStats) {
            if (channel == null || channel.getChannelId() == null) {
                return null;
            }
            long dayStart = resolveHelper.resolveDayStart(currency);
            int orderType = isCollection ? CommonConstant.SUPPORT_TYPE_COLLECTION : CommonConstant.SUPPORT_TYPE_PAY;
            Map<String, ChannelReportDto> stats = isCollection ? collectionStats : payoutStats;
            ChannelReportDto report = stats.get(channel.getChannelId() + "|" + currency);
            ChannelReportDto dto = fillHelper.fillChannelReportDefaults(report, channel, currency, orderType, dayStart);
            return dto;
        }

        /**
         * Apply payment report aggregation and persistence.
         *
         * @param payment payment info
         * @param currency currency code
         * @param isCollection true for collection, false for payout
         * @param collectionStats collection stats map
         * @param payoutStats payout stats map
         */
        private PaymentReportDto applyPaymentReport(PaymentDto payment,
                                                    String currency,
                                                    boolean isCollection,
                                                    Map<String, PaymentReportDto> collectionStats,
                                                    Map<String, PaymentReportDto> payoutStats) {
            if (payment == null || payment.getPaymentId() == null) {
                return null;
            }
            long dayStart = resolveHelper.resolveDayStart(currency);
            int orderType = isCollection ? CommonConstant.SUPPORT_TYPE_COLLECTION : CommonConstant.SUPPORT_TYPE_PAY;
            Map<String, PaymentReportDto> stats = isCollection ? collectionStats : payoutStats;
            PaymentReportDto report = stats.get(payment.getPaymentId() + "|" + currency);
            PaymentReportDto dto = fillHelper.fillPaymentReportDefaults(report, payment, currency, orderType, dayStart);
            return dto;
        }

        /**
         * Apply currency report aggregation and persistence.
         *
         * @param currency currency code
         * @param isCollection true for collection, false for payout
         * @param collectionStats collection stats map
         * @param payoutStats payout stats map
         */
        private CurrencyReportDto applyCurrencyReport(String currency,
                                                      boolean isCollection,
                                                      Map<String, CurrencyReportDto> collectionStats,
                                                      Map<String, CurrencyReportDto> payoutStats) {
            long dayStart = resolveHelper.resolveDayStart(currency);
            int orderType = isCollection ? CommonConstant.SUPPORT_TYPE_COLLECTION : CommonConstant.SUPPORT_TYPE_PAY;
            Map<String, CurrencyReportDto> stats = isCollection ? collectionStats : payoutStats;
            CurrencyReportDto report = stats.get(currency);
            CurrencyReportDto dto = fillHelper.fillCurrencyReportDefaults(report, currency, orderType, dayStart);
            return dto;
        }

        /**
         * Process entities for collection/payout reports using a shared template.
         *
         * @param entities entity list
         * @param currencyResolver currency resolver
         * @param applier report applier
         */
        private <E, R> List<R> processDualSupportReports(
                List<E> entities,
                SupportCurrencyResolver<E> currencyResolver,
                SupportReportApplier<E, R> applier) {
            log.info("processDualSupportReports start, entityCount={}", entities == null ? 0 : entities.size());
            if (entities == null || entities.isEmpty()) {
                log.info("processDualSupportReports end, reportCount=0");
                return Collections.emptyList();
            }
            List<R> reports = new ArrayList<>(entities.size() * 2);
            for (E entity : CommonUtil.safeList(entities)) {
                for (String currency : safeSet(currencyResolver.resolve(entity, true))) {
                    R dto = applier.apply(entity, currency, true);
                    if (dto != null) {
                        reports.add(dto);
                    }
                }
                for (String currency : safeSet(currencyResolver.resolve(entity, false))) {
                    R dto = applier.apply(entity, currency, false);
                    if (dto != null) {
                        reports.add(dto);
                    }
                }
            }
            log.info("processDualSupportReports end, reportCount={}", reports.size());
            return reports;
        }

        /**
         * Safe empty set fallback.
         *
         * @param set input set
         * @return safe set
         */
        private <T> Set<T> safeSet(Set<T> set) {
            return set == null ? Collections.emptySet() : set;
        }

        /**
         * Load report stats by currency and build lookup map.
         *
         * @param currencies currency set
         * @param loader report stats loader
         * @param keyFn key builder
         * @return stats map
         */
        private <T> Map<String, T> loadStatsByCurrency(
                Set<String> currencies,
                ReportStatsBatchLoader<T> loader,
                Function<T, String> keyFn) {
            Map<String, T> stats = new HashMap<>();
            if (currencies == null || currencies.isEmpty()) {
                return stats;
            }
            log.info("loadStatsByCurrency start, currencyCount={}", currencies.size());
            List<ReportCurrencyRange> ranges = new ArrayList<>(currencies.size());
            String successStatus = TransactionStatus.SUCCESS.getCode().toString();
            for (String currency : currencies) {
                long dayStart = resolveHelper.resolveDayStart(currency);
                long nextDayStart = resolveHelper.resolveNextDayStart(currency);
                ranges.add(new ReportCurrencyRange(currency, dayStart, nextDayStart));
            }
            List<T> list = loader.load(ranges, successStatus);
            log.info("loadStatsByCurrency loaded, rangeCount={}, rowCount={}",
                    ranges.size(),
                    list == null ? 0 : list.size());
            for (T dto : CommonUtil.safeList(list)) {
                if (dto == null) {
                    continue;
                }
                String key = keyFn.apply(dto);
                if (key == null) {
                    continue;
                }
                stats.put(key, dto);
            }
            log.info("loadStatsByCurrency end, statsCount={}", stats.size());
            return stats;
        }
    }

    private final class FillHelper {
        /**
         * Fill merchant report defaults and base fields.
         *
         * @param report existing report
         * @param merchant merchant info
         * @param currency currency code
         * @param orderType order type
         * @param recordDate record date
         * @return report dto
         */
        private MerchantReportDto fillReportDefaults(
                MerchantReportDto report,
                MerchantInfoDto merchant,
                String currency,
                Integer orderType,
                long recordDate) {
            boolean isNew = report == null; // createTime only on insert
            MerchantReportDto dto = report == null ? new MerchantReportDto() : report;
            PatchBuilderUtil.from(report).to(dto)
                    .obj(merchant::getUserId, dto::setUserId)
                    .obj(merchant::getMerchantName, dto::setMerchantName)
                    .obj(() -> orderType, dto::setOrderType)
                    .obj(() -> currency, dto::setCurrency)
                    .obj(() -> recordDate, dto::setRecordDate)
                    .ifTrue(isNew).obj(() -> nowEpochSecond, dto::setCreateTime).endSkip()
                    .obj(() -> nowEpochSecond, dto::setUpdateTime)
                    .obj(() -> dto.getCreateTime() == null ? nowEpochSecond : dto.getCreateTime(), dto::setCreateTime)
                    .obj(() -> CommonUtil.defaultInt(dto.getOrderQuantity(), 0), dto::setOrderQuantity)
                    .obj(() -> CommonUtil.defaultInt(dto.getSuccessQuantity(), 0), dto::setSuccessQuantity)
                    .obj(() -> CommonUtil.defaultBigDecimal(dto.getMerchantFee()), dto::setMerchantFee)
                    .obj(() -> CommonUtil.defaultBigDecimal(dto.getAgent1Fee()), dto::setAgent1Fee)
                    .obj(() -> CommonUtil.defaultBigDecimal(dto.getAgent2Fee()), dto::setAgent2Fee)
                    .obj(() -> CommonUtil.defaultBigDecimal(dto.getAgent3Fee()), dto::setAgent3Fee);
            dto.setOrderProfit(resolveOrderProfit(dto));
            return dto;
        }

        /**
         * Fill channel report defaults and base fields.
         *
         * @param report existing report
         * @param channel channel info
         * @param currency currency code
         * @param orderType order type
         * @param recordDate record date
         * @return report dto
         */
        private ChannelReportDto fillChannelReportDefaults(
                ChannelReportDto report,
                ChannelDto channel,
                String currency,
                Integer orderType,
                long recordDate) {
            boolean isNew = report == null; // createTime only on insert
            ChannelReportDto dto = report == null ? new ChannelReportDto() : report;
            PatchBuilderUtil.from(report).to(dto)
                    .obj(channel::getChannelId, dto::setChannelId)
                    .obj(channel::getChannelName, dto::setChannelName)
                    .obj(() -> currency, dto::setCurrency)
                    .obj(() -> orderType, dto::setOrderType)
                    .obj(() -> recordDate, dto::setRecordDate)
                    .ifTrue(isNew).obj(() -> nowEpochSecond, dto::setCreateTime).endSkip()
                    .obj(() -> nowEpochSecond, dto::setUpdateTime)
                    .obj(() -> dto.getCreateTime() == null ? nowEpochSecond : dto.getCreateTime(), dto::setCreateTime)
                    .obj(() -> CommonUtil.defaultInt(dto.getOrderQuantity(), 0), dto::setOrderQuantity)
                    .obj(() -> CommonUtil.defaultInt(dto.getSuccessQuantity(), 0), dto::setSuccessQuantity)
                    .obj(() -> CommonUtil.defaultBigDecimal(dto.getOrderBalance()), dto::setOrderBalance)
                    .obj(() -> CommonUtil.defaultBigDecimal(dto.getMerchantFee()), dto::setMerchantFee)
                    .obj(() -> CommonUtil.defaultBigDecimal(dto.getOrderProfit()), dto::setOrderProfit);
            return dto;
        }

        /**
         * Fill payment report defaults and base fields.
         *
         * @param report existing report
         * @param payment payment info
         * @param currency currency code
         * @param orderType order type
         * @param recordDate record date
         * @return report dto
         */
        private PaymentReportDto fillPaymentReportDefaults(
                PaymentReportDto report,
                PaymentDto payment,
                String currency,
                Integer orderType,
                long recordDate) {
            boolean isNew = report == null; // createTime only on insert
            PaymentReportDto dto = report == null ? new PaymentReportDto() : report;
            PatchBuilderUtil.from(report).to(dto)
                    .obj(payment::getPaymentId, dto::setPaymentId)
                    .obj(payment::getPaymentName, dto::setPaymentName)
                    .obj(() -> currency, dto::setCurrency)
                    .obj(() -> orderType, dto::setOrderType)
                    .obj(() -> recordDate, dto::setRecordDate)
                    .ifTrue(isNew).obj(() -> nowEpochSecond, dto::setCreateTime).endSkip()
                    .obj(() -> nowEpochSecond, dto::setUpdateTime)
                    .obj(() -> dto.getCreateTime() == null ? nowEpochSecond : dto.getCreateTime(), dto::setCreateTime)
                    .obj(() -> CommonUtil.defaultInt(dto.getOrderQuantity(), 0), dto::setOrderQuantity)
                    .obj(() -> CommonUtil.defaultInt(dto.getSuccessQuantity(), 0), dto::setSuccessQuantity)
                    .obj(() -> CommonUtil.defaultBigDecimal(dto.getOrderBalance()), dto::setOrderBalance)
                    .obj(() -> CommonUtil.defaultBigDecimal(dto.getMerchantFee()), dto::setMerchantFee)
                    .obj(() -> CommonUtil.defaultBigDecimal(dto.getOrderProfit()), dto::setOrderProfit);
            return dto;
        }

        /**
         * Fill currency report defaults and base fields.
         *
         * @param report existing report
         * @param currency currency code
         * @param orderType order type
         * @param recordDate record date
         * @return report dto
         */
        private CurrencyReportDto fillCurrencyReportDefaults(
                CurrencyReportDto report,
                String currency,
                Integer orderType,
                long recordDate) {
            boolean isNew = report == null; // createTime only on insert
            CurrencyReportDto dto = report == null ? new CurrencyReportDto() : report;
            PatchBuilderUtil.from(report).to(dto)
                    .obj(() -> currency, dto::setCurrency)
                    .obj(() -> orderType, dto::setOrderType)
                    .obj(() -> recordDate, dto::setRecordDate)
                    .ifTrue(isNew).obj(() -> nowEpochSecond, dto::setCreateTime).endSkip()
                    .obj(() -> nowEpochSecond, dto::setUpdateTime)
                    .obj(() -> dto.getCreateTime() == null ? nowEpochSecond : dto.getCreateTime(), dto::setCreateTime)
                    .obj(() -> CommonUtil.defaultInt(dto.getOrderQuantity(), 0), dto::setOrderQuantity)
                    .obj(() -> CommonUtil.defaultInt(dto.getSuccessQuantity(), 0), dto::setSuccessQuantity)
                    .obj(() -> CommonUtil.defaultBigDecimal(dto.getOrderBalance()), dto::setOrderBalance)
                    .obj(() -> CommonUtil.defaultBigDecimal(dto.getMerchantFee()), dto::setMerchantFee)
                    .obj(() -> CommonUtil.defaultBigDecimal(dto.getOrderProfit()), dto::setOrderProfit);
            return dto;
        }

        /**
         * Resolve merchant profit after agent fees.
         *
         * @param dto merchant report
         * @return profit value
         */
        private BigDecimal resolveOrderProfit(MerchantReportDto dto) {
            BigDecimal profit = dto.getMerchantFee() == null ? BigDecimal.ZERO : dto.getMerchantFee();
            profit = CommonUtil.safeSubtract(profit, dto.getAgent1Fee() == null ? BigDecimal.ZERO : dto.getAgent1Fee());
            profit = CommonUtil.safeSubtract(profit, dto.getAgent2Fee() == null ? BigDecimal.ZERO : dto.getAgent2Fee());
            profit = CommonUtil.safeSubtract(profit, dto.getAgent3Fee() == null ? BigDecimal.ZERO : dto.getAgent3Fee());
            return profit;
        }
    }

    private final class PersistHelper {
        private static final int REPORT_UPSERT_BATCH_SIZE = 1000;

        /**
         * Batch upsert merchant reports.
         *
         * @param list report list
         */
        private void batchUpsertMerchantReports(List<MerchantReportDto> list) {
            batchUpsert(list, merchantReportMapper::batchUpsert, "merchantReport");
        }

        /**
         * Batch upsert channel reports.
         *
         * @param list report list
         */
        private void batchUpsertChannelReports(List<ChannelReportDto> list) {
            batchUpsert(list, channelReportMapper::batchUpsert, "channelReport");
        }

        /**
         * Batch upsert payment reports.
         *
         * @param list report list
         */
        private void batchUpsertPaymentReports(List<PaymentReportDto> list) {
            batchUpsert(list, paymentReportMapper::batchUpsert, "paymentReport");
        }

        /**
         * Batch upsert currency reports.
         *
         * @param list report list
         */
        private void batchUpsertCurrencyReports(List<CurrencyReportDto> list) {
            batchUpsert(list, currencyReportMapper::batchUpsert, "currencyReport");
        }

        /**
         * Batch upsert agent reports.
         *
         * @param list report list
         */
        private void batchUpsertAgentReports(List<AgentReportDto> list) {
            batchUpsert(list, agentReportMapper::batchUpsert, "agentReport");
        }

        /**
         * Batch upsert reports with chunking.
         *
         * @param list report list
         * @param runner batch upsert runner
         * @param reportName report name
         * @param <T> report type
         */
        private <T> int batchUpsert(
                List<T> list,
                java.util.function.Function<List<T>, Integer> runner,
                String reportName) {
            if (list == null || list.isEmpty()) {
                log.info("batchUpsert {} skipped, recordCount=0", reportName);
                return 0;
            }
            int affected = 0;
            int total = list.size();
            for (int i = 0; i < total; i += REPORT_UPSERT_BATCH_SIZE) {
                int end = Math.min(i + REPORT_UPSERT_BATCH_SIZE, total);
                List<T> slice = list.subList(i, end);
                affected += runner.apply(slice);
            }
            log.info("batchUpsert {} done, recordCount={}, affected={}", reportName, total, affected);
            return affected;
        }
    }

    private final class AgentHelper {
        /**
         * Accumulate agent report data from merchant report.
         *
         * @param merchant merchant info
         * @param merchantReport merchant report
         * @param currency currency code
         * @param orderType order type
         * @param recordDate record date
         */
        void accumulateAgentReport(MerchantInfoDto merchant,
                                   MerchantReportDto merchantReport,
                                   String currency,
                                   Integer orderType,
                                   long recordDate) {
            if (merchant == null || merchant.getAgentInfos() == null || merchant.getAgentInfos().isEmpty()) {
                return;
            }
            List<AgentInfoDto> chain = merchant.getAgentInfos();
            AgentInfoDto agent1 = getAgentFromChain(chain, 1);
            AgentInfoDto agent2 = getAgentFromChain(chain, 2);
            AgentInfoDto agent3 = getAgentFromChain(chain, 3);

            addAgentReport(agent1, merchantReport, currency, orderType, recordDate,
                    merchantReport == null ? BigDecimal.ZERO : merchantReport.getAgent1Fee());
            addAgentReport(agent2, merchantReport, currency, orderType, recordDate,
                    merchantReport == null ? BigDecimal.ZERO : merchantReport.getAgent2Fee());
            addAgentReport(agent3, merchantReport, currency, orderType, recordDate,
                    merchantReport == null ? BigDecimal.ZERO : merchantReport.getAgent3Fee());
        }

        /**
         * Get agent by level from chain.
         *
         * @param chain agent chain
         * @param level level (1/2/3)
         * @return agent info
         */
        private AgentInfoDto getAgentFromChain(List<AgentInfoDto> chain, int level) {
            if (chain == null || chain.isEmpty() || level <= 0) {
                return null;
            }
            int indexFromBottom = chain.size() - level;
            if (indexFromBottom < 0 || indexFromBottom >= chain.size()) {
                return null;
            }
            return chain.get(indexFromBottom);
        }

        /**
         * Add or update an agent report entry.
         *
         * @param agent agent info
         * @param merchantReport merchant report
         * @param currency currency code
         * @param orderType order type
         * @param recordDate record date
         * @param commission commission value
         */
        private void addAgentReport(AgentInfoDto agent,
                                    MerchantReportDto merchantReport,
                                    String currency,
                                    Integer orderType,
                                    long recordDate,
                                    BigDecimal commission) {
            if (agent == null || agent.getUserId() == null) {
                return;
            }
            String key = agent.getUserId() + "|" + currency + "|" + orderType + "|" + recordDate;
            AgentReportDto dto = agentReportMap.get(key);
            if (dto == null) {
                dto = new AgentReportDto();
                dto.setUserId(agent.getUserId());
                dto.setAgentName(agent.getAgentName());
                dto.setOrderType(orderType);
                dto.setCurrency(currency);
                dto.setRecordDate(recordDate);
                dto.setCreateTime(nowEpochSecond);
                dto.setUpdateTime(nowEpochSecond);
                dto.setOrderQuantity(0);
                dto.setSuccessQuantity(0);
                dto.setCommission(BigDecimal.ZERO);
                agentReportMap.put(key, dto);
            }
            dto.setOrderQuantity(dto.getOrderQuantity() + safeInt(merchantReport == null ? null : merchantReport.getOrderQuantity()));
            dto.setSuccessQuantity(dto.getSuccessQuantity() + safeInt(merchantReport == null ? null : merchantReport.getSuccessQuantity()));
            dto.setCommission(CommonUtil.safeAdd(dto.getCommission(), commission));
            dto.setUpdateTime(nowEpochSecond);
        }

        /**
         * Safe integer fallback.
         *
         * @param value input value
         * @return safe int
         */
        private int safeInt(Integer value) {
            return value == null ? 0 : value;
        }

        /**
         * Upsert all agent reports.
         */
        private void upsertAgentReports() {
            if (agentReportMap == null || agentReportMap.isEmpty()) {
                return;
            }
            log.info("upsertAgentReports start, reportCount={}", agentReportMap.size());
            List<AgentReportDto> list = new ArrayList<>(agentReportMap.values());
            persistHelper.batchUpsertAgentReports(list);
            log.info("upsertAgentReports end");
        }
    }

    private final class OpsHelper {
        private static final int SCOPE_ALL = 0;
        private static final int SCOPE_MERCHANT = 1;
        private static final int SCOPE_AGENT = 2;
        private static final String ALL_SCOPE_ID = "0";
        private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

        /**
         * Generate and upsert daily ops reports.
         */
        private void reportOpsDaily() {
            log.info("ops report daily start");
            Map<String, OpsRecord> records = buildOpsRecords(OpsPeriod.DAILY);
            upsertDaily(records);
            log.info("ops report daily end, recordCount={}", records.size());
        }

        /**
         * Generate and upsert monthly ops reports.
         */
        private void reportOpsMonthly() {
            log.info("ops report monthly start");
            Map<String, OpsRecord> records = buildOpsRecords(OpsPeriod.MONTHLY);
            upsertMonthly(records);
            log.info("ops report monthly end, recordCount={}", records.size());
        }

        /**
         * Generate and upsert yearly ops reports.
         */
        private void reportOpsYearly() {
            log.info("ops report yearly start");
            Map<String, OpsRecord> records = buildOpsRecords(OpsPeriod.YEARLY);
            upsertYearly(records);
            log.info("ops report yearly end, recordCount={}", records.size());
        }

        /**
         * Build ops records for the target period.
         *
         * @param period report period
         * @return ops record map
         */
        private Map<String, OpsRecord> buildOpsRecords(OpsPeriod period) {
            Map<String, OpsRecord> records = new HashMap<>();
            Set<String> currencies = resolveHelper.resolveAllCurrencies();
            log.info("build ops records start, period={}, currencyCount={}", period, currencies.size());
            Map<String, List<MerchantReportDto>> collectionStatsByCurrency = loadOpsStatsByCurrency(
                    currencies,
                    period,
                    collectionOrderMapper::listMerchantReportStatsBatch);
            Map<String, List<MerchantReportDto>> payoutStatsByCurrency = loadOpsStatsByCurrency(
                    currencies,
                    period,
                    payOrderMapper::listMerchantReportStatsBatch);
            for (String currency : currencies) {
                long startTime = resolvePeriodStart(period, currency);
                long endTime = resolvePeriodEnd(period, currency);
                LocalDate baseDate = resolveReportBaseDate(currency);
                String reportMonth = baseDate.format(MONTH_FORMAT);
                String reportYear = String.valueOf(baseDate.getYear());
                OpsContext baseContext = buildOpsContext(period, baseDate, reportMonth, reportYear, currency);
                log.info("build ops records currency scope, period={}, currency={}, startTime={}, endTime={}, baseDate={}",
                        period, currency, startTime, endTime, baseDate);

                OpsOrderContext collectionContext = buildOpsOrderContext(baseContext,
                        CommonConstant.SUPPORT_TYPE_COLLECTION, startTime, endTime);
                buildOpsByOrderType(records, collectionContext, collectionStatsByCurrency);

                OpsOrderContext payoutContext = buildOpsOrderContext(baseContext,
                        CommonConstant.SUPPORT_TYPE_PAY, startTime, endTime);
                buildOpsByOrderType(records, payoutContext, payoutStatsByCurrency);
            }
            log.info("build ops records end, period={}, recordCount={}", period, records.size());
            return records;
        }

        /**
         * Build ops records by order type for a period and currency.
         * Uses in-memory stats grouped by currency (batch loaded).
         *
         * @param records record map
         * @param context order context
         * @param statsByCurrency stats grouped by currency
         */
        private void buildOpsByOrderType(Map<String, OpsRecord> records,
                                         OpsOrderContext context,
                                         Map<String, List<MerchantReportDto>> statsByCurrency) {
            List<MerchantReportDto> stats = statsByCurrency.get(context.currency);
            log.info("build ops by orderType loaded, period={}, currency={}, orderType={}, statsCount={}",
                    context.period, context.currency, context.orderType, stats == null ? 0 : stats.size());
            for (MerchantReportDto dto : CommonUtil.safeList(stats)) {
                if (dto == null || dto.getUserId() == null) {
                    continue;
                }
                OpsTotals totals = buildOpsTotals(dto, sumAgentCommission(dto));

                OpsScope merchantScope = buildOpsScope(SCOPE_MERCHANT, dto.getUserId());
                String merchantKey = buildOpsKey(context, merchantScope);
                accumulateOps(records, merchantKey, context, merchantScope, totals);

                OpsScope allScope = buildOpsScope(SCOPE_ALL, ALL_SCOPE_ID);
                String allKey = buildOpsKey(context, allScope);
                accumulateOps(records, allKey, context, allScope, totals);

                MerchantInfoDto merchantInfo = merchantByUserIdCache.get(dto.getUserId());
                if (merchantInfo == null || merchantInfo.getAgentInfos() == null) {
                    continue;
                }
                accumulateAgentOps(records, context, merchantInfo.getAgentInfos(), dto, totals);
            }
        }

        /**
         * Load ops stats once per period, grouped by currency.
         *
         * @param currencies currency set
         * @param period report period
         * @param loader stats batch loader
         * @return stats map by currency
         */
        private Map<String, List<MerchantReportDto>> loadOpsStatsByCurrency(Set<String> currencies,
                                                                            OpsPeriod period,
                                                                            ReportStatsBatchLoader<MerchantReportDto> loader) {
            Map<String, List<MerchantReportDto>> statsByCurrency = new HashMap<>();
            if (currencies == null || currencies.isEmpty()) {
                return statsByCurrency;
            }
            List<ReportCurrencyRange> ranges = new ArrayList<>(currencies.size());
            for (String currency : currencies) {
                long startTime = resolvePeriodStart(period, currency);
                long endTime = resolvePeriodEnd(period, currency);
                ranges.add(new ReportCurrencyRange(currency, startTime, endTime));
            }
            String successStatus = TransactionStatus.SUCCESS.getCode().toString();
            List<MerchantReportDto> stats = loader.load(ranges, successStatus);
            log.info("build ops stats loaded, period={}, rangeCount={}, rowCount={}",
                    period,
                    ranges.size(),
                    stats == null ? 0 : stats.size());
            for (MerchantReportDto dto : CommonUtil.safeList(stats)) {
                if (dto == null || dto.getCurrency() == null) {
                    continue;
                }
                statsByCurrency.computeIfAbsent(dto.getCurrency(), key -> new ArrayList<>()).add(dto);
            }
            return statsByCurrency;
        }

        /**
         * Accumulate ops records for agent scope.
         *
         * @param records record map
         * @param period report period
         * @param reportDate report date
         * @param reportMonth report month
         * @param reportYear report year
         * @param orderType order type
         * @param currency currency code
         * @param agentInfos agent chain
         * @param merchantReport merchant report
         * @param total order total
         * @param success order success
         */
        /**
         * Accumulate ops records for agent scope.
         *
         * @param records record map
         * @param context order context
         * @param agentInfos agent chain
         * @param merchantReport merchant report
         * @param totals order totals
         */
        private void accumulateAgentOps(Map<String, OpsRecord> records,
                                        OpsOrderContext context,
                                        List<AgentInfoDto> agentInfos,
                                        MerchantReportDto merchantReport,
                                        OpsTotals totals) {
            if (agentInfos == null || agentInfos.isEmpty()) {
                return;
            }
            for (AgentInfoDto agent : agentInfos) {
                if (agent == null || agent.getUserId() == null || agent.getLevel() == null) {
                    continue;
                }
                BigDecimal agentFee = resolveAgentFeeByLevel(merchantReport, agent.getLevel());
                OpsScope agentScope = buildOpsScope(SCOPE_AGENT, agent.getUserId());
                OpsTotals agentTotals = buildOpsTotals(totals.total, totals.success, agentFee);
                String key = buildOpsKey(context, agentScope);
                accumulateOps(records, key, context, agentScope, agentTotals);
            }
        }

        /**
         * Accumulate ops record by key.
         *
         * @param records record map
         * @param key record key
         * @param context order context
         * @param scope record scope
         * @param totals order totals
         */
        private void accumulateOps(Map<String, OpsRecord> records,
                                   String key,
                                   OpsOrderContext context,
                                   OpsScope scope,
                                   OpsTotals totals) {
            OpsRecord record = records.computeIfAbsent(key, k -> new OpsRecord());
            record.period = context.period;
            record.reportDate = context.reportDate;
            record.reportMonth = context.reportMonth;
            record.reportYear = context.reportYear;
            record.orderType = context.orderType;
            record.currency = context.currency;
            record.scopeType = scope.scopeType;
            record.scopeId = scope.scopeId;
            record.orderQuantity += totals.total;
            record.successQuantity += totals.success;
            record.failQuantity += Math.max(0, totals.total - totals.success);
            record.agentCommission = CommonUtil.safeAdd(record.agentCommission, totals.agentCommission);
        }

        /**
         * Upsert daily ops records.
         *
         * @param records record map
         */
        private void upsertDaily(Map<String, OpsRecord> records) {
            List<OpsOrderDailyDto> list = new ArrayList<>();
            for (OpsRecord record : records.values()) {
                if (record.period != OpsPeriod.DAILY) {
                    continue;
                }
                list.add(fillOpsDailyDto(record));
            }
            if (list.isEmpty()) {
                log.info("upsert ops daily skipped, recordCount=0");
                return;
            }
            int affected = persistHelper.batchUpsert(list, opsOrderDailyMapper::batchUpsert, "opsDaily");
            log.info("upsert ops daily done, recordCount={}, affected={}", list.size(), affected);
        }

        /**
         * Upsert monthly ops records.
         *
         * @param records record map
         */
        private void upsertMonthly(Map<String, OpsRecord> records) {
            List<OpsOrderMonthlyDto> list = new ArrayList<>();
            for (OpsRecord record : records.values()) {
                if (record.period != OpsPeriod.MONTHLY) {
                    continue;
                }
                list.add(fillOpsMonthlyDto(record));
            }
            if (list.isEmpty()) {
                log.info("upsert ops monthly skipped, recordCount=0");
                return;
            }
            int affected = persistHelper.batchUpsert(list, opsOrderMonthlyMapper::batchUpsert, "opsMonthly");
            log.info("upsert ops monthly done, recordCount={}, affected={}", list.size(), affected);
        }

        /**
         * Upsert yearly ops records.
         *
         * @param records record map
         */
        private void upsertYearly(Map<String, OpsRecord> records) {
            List<OpsOrderYearlyDto> list = new ArrayList<>();
            for (OpsRecord record : records.values()) {
                if (record.period != OpsPeriod.YEARLY) {
                    continue;
                }
                list.add(fillOpsYearlyDto(record));
            }
            if (list.isEmpty()) {
                log.info("upsert ops yearly skipped, recordCount=0");
                return;
            }
            int affected = persistHelper.batchUpsert(list, opsOrderYearlyMapper::batchUpsert, "opsYearly");
            log.info("upsert ops yearly done, recordCount={}, affected={}", list.size(), affected);
        }

        /**
         * Fill daily ops dto from record.
         *
         * @param record ops record
         * @return daily dto
         */
        private OpsOrderDailyDto fillOpsDailyDto(OpsRecord record) {
            OpsOrderDailyDto dto = new OpsOrderDailyDto();
            dto.setReportDate(record.reportDate);
            dto.setOrderType(record.orderType);
            dto.setCurrency(record.currency);
            dto.setScopeType(record.scopeType);
            dto.setScopeId(record.scopeId);
            dto.setOrderQuantity(record.orderQuantity);
            dto.setSuccessQuantity(record.successQuantity);
            dto.setFailQuantity(record.failQuantity);
            dto.setSuccessRate(resolveSuccessRate(record.successQuantity, record.orderQuantity));
            dto.setAgentCommission(record.agentCommission);
            dto.setCreateTime(nowEpochSecond);
            dto.setUpdateTime(nowEpochSecond);
            return dto;
        }

        /**
         * Fill monthly ops dto from record.
         *
         * @param record ops record
         * @return monthly dto
         */
        private OpsOrderMonthlyDto fillOpsMonthlyDto(OpsRecord record) {
            OpsOrderMonthlyDto dto = new OpsOrderMonthlyDto();
            dto.setReportMonth(record.reportMonth);
            dto.setOrderType(record.orderType);
            dto.setCurrency(record.currency);
            dto.setScopeType(record.scopeType);
            dto.setScopeId(record.scopeId);
            dto.setOrderQuantity(record.orderQuantity);
            dto.setSuccessQuantity(record.successQuantity);
            dto.setFailQuantity(record.failQuantity);
            dto.setSuccessRate(resolveSuccessRate(record.successQuantity, record.orderQuantity));
            dto.setAgentCommission(record.agentCommission);
            dto.setCreateTime(nowEpochSecond);
            dto.setUpdateTime(nowEpochSecond);
            return dto;
        }

        /**
         * Fill yearly ops dto from record.
         *
         * @param record ops record
         * @return yearly dto
         */
        private OpsOrderYearlyDto fillOpsYearlyDto(OpsRecord record) {
            OpsOrderYearlyDto dto = new OpsOrderYearlyDto();
            dto.setReportYear(record.reportYear);
            dto.setOrderType(record.orderType);
            dto.setCurrency(record.currency);
            dto.setScopeType(record.scopeType);
            dto.setScopeId(record.scopeId);
            dto.setOrderQuantity(record.orderQuantity);
            dto.setSuccessQuantity(record.successQuantity);
            dto.setFailQuantity(record.failQuantity);
            dto.setSuccessRate(resolveSuccessRate(record.successQuantity, record.orderQuantity));
            dto.setAgentCommission(record.agentCommission);
            dto.setCreateTime(nowEpochSecond);
            dto.setUpdateTime(nowEpochSecond);
            return dto;
        }

        /**
         * Resolve period start epoch seconds by currency.
         *
         * @param period report period
         * @param currency currency code
         * @return start epoch seconds
         */
        private long resolvePeriodStart(OpsPeriod period, String currency) {
            return switch (period) {
                case DAILY -> resolveHelper.resolveDayStart(currency);
                case MONTHLY -> resolveHelper.resolveMonthStart(currency);
                case YEARLY -> resolveHelper.resolveYearStart(currency);
            };
        }

        /**
         * Resolve period end epoch seconds by currency.
         *
         * @param period report period
         * @param currency currency code
         * @return end epoch seconds
         */
        private long resolvePeriodEnd(OpsPeriod period, String currency) {
            return switch (period) {
                case DAILY -> resolveHelper.resolveNextDayStart(currency);
                case MONTHLY -> resolveHelper.resolveNextMonthStart(currency);
                case YEARLY -> resolveHelper.resolveNextYearStart(currency);
            };
        }

        /**
         * Resolve report base date by currency.
         *
         * @param currency currency code
         * @return base date
         */
        private LocalDate resolveReportBaseDate(String currency) {
            return resolveHelper.resolveReportBaseDate(currency, CommonUtil.resolveZoneIdByCurrency(currency));
        }

        /**
         * Default long value for nullable integer.
         *
         * @param value input value
         * @return safe long
         */
        private long defaultLong(Integer value) {
            return value == null ? 0L : value.longValue();
        }

        /**
         * Sum agent commission from merchant report.
         *
         * @param dto merchant report
         * @return agent commission sum
         */
        private BigDecimal sumAgentCommission(MerchantReportDto dto) {
            BigDecimal a1 = dto.getAgent1Fee() == null ? BigDecimal.ZERO : dto.getAgent1Fee();
            BigDecimal a2 = dto.getAgent2Fee() == null ? BigDecimal.ZERO : dto.getAgent2Fee();
            BigDecimal a3 = dto.getAgent3Fee() == null ? BigDecimal.ZERO : dto.getAgent3Fee();
            return CommonUtil.safeAdd(a1, a2, a3);
        }

        /**
         * Resolve agent fee by level.
         *
         * @param dto merchant report
         * @param level agent level
         * @return fee amount
         */
        private BigDecimal resolveAgentFeeByLevel(MerchantReportDto dto, Integer level) {
            if (CommonConstant.AGENT_LEVEL_FIRST.equals(level)) {
                return dto.getAgent1Fee() == null ? BigDecimal.ZERO : dto.getAgent1Fee();
            }
            if (CommonConstant.AGENT_LEVEL_SECOND.equals(level)) {
                return dto.getAgent2Fee() == null ? BigDecimal.ZERO : dto.getAgent2Fee();
            }
            if (CommonConstant.AGENT_LEVEL_THIRD.equals(level)) {
                return dto.getAgent3Fee() == null ? BigDecimal.ZERO : dto.getAgent3Fee();
            }
            return BigDecimal.ZERO;
        }

        /**
         * Resolve success rate for totals.
         *
         * @param success success count
         * @param total total count
         * @return success rate
         */
        private BigDecimal resolveSuccessRate(long success, long total) {
            if (total <= 0) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(success)
                    .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        }


        /**
         * Build key for ops record aggregation.
         *
         * @param context order context
         * @param scope record scope
         * @return record key
         */
        private String buildOpsKey(OpsOrderContext context, OpsScope scope) {
            String periodKey = switch (context.period) {
                case DAILY -> context.reportDate == null ? "" : context.reportDate.toString();
                case MONTHLY -> context.reportMonth == null ? "" : context.reportMonth;
                case YEARLY -> context.reportYear == null ? "" : context.reportYear;
            };
            return periodKey + "|" + context.orderType + "|" + context.currency + "|" + scope.scopeType + "|" + scope.scopeId;
        }

        /**
         * Build base ops context.
         *
         * @param period report period
         * @param reportDate report date
         * @param reportMonth report month
         * @param reportYear report year
         * @param currency currency code
         * @return base context
         */
        private OpsContext buildOpsContext(OpsPeriod period,
                                           LocalDate reportDate,
                                           String reportMonth,
                                           String reportYear,
                                           String currency) {
            OpsContext context = new OpsContext();
            context.period = period;
            context.reportDate = reportDate;
            context.reportMonth = reportMonth;
            context.reportYear = reportYear;
            context.currency = currency;
            return context;
        }

        /**
         * Build order context from base context.
         *
         * @param baseContext base context
         * @param orderType order type
         * @param startTime start time epoch seconds
         * @param endTime end time epoch seconds
         * @return order context
         */
        private OpsOrderContext buildOpsOrderContext(OpsContext baseContext,
                                                     Integer orderType,
                                                     long startTime,
                                                     long endTime) {
            OpsOrderContext context = new OpsOrderContext();
            context.period = baseContext.period;
            context.reportDate = baseContext.reportDate;
            context.reportMonth = baseContext.reportMonth;
            context.reportYear = baseContext.reportYear;
            context.currency = baseContext.currency;
            context.orderType = orderType;
            context.startTime = startTime;
            context.endTime = endTime;
            return context;
        }

        /**
         * Build scope descriptor.
         *
         * @param scopeType scope type
         * @param scopeId scope id
         * @return scope descriptor
         */
        private OpsScope buildOpsScope(Integer scopeType, String scopeId) {
            OpsScope scope = new OpsScope();
            scope.scopeType = scopeType;
            scope.scopeId = scopeId;
            return scope;
        }

        /**
         * Build totals from merchant stats.
         *
         * @param dto merchant stats
         * @param agentCommission agent commission
         * @return totals
         */
        private OpsTotals buildOpsTotals(MerchantReportDto dto, BigDecimal agentCommission) {
            return buildOpsTotals(defaultLong(dto.getOrderQuantity()), defaultLong(dto.getSuccessQuantity()), agentCommission);
        }

        /**
         * Build totals from raw values.
         *
         * @param total order total
         * @param success success total
         * @param agentCommission agent commission
         * @return totals
         */
        private OpsTotals buildOpsTotals(long total, long success, BigDecimal agentCommission) {
            OpsTotals totals = new OpsTotals();
            totals.total = total;
            totals.success = success;
            totals.agentCommission = agentCommission == null ? BigDecimal.ZERO : agentCommission;
            return totals;
        }
    }

    private final class ResolveHelper {
        /**
         * Reset in-memory caches for report generation.
         */
        private void resetReportCache() {
            merchantInfoCache = Collections.emptyList();
            channelInfoCache = Collections.emptyList();
            paymentInfoCache = Collections.emptyList();
            agentInfoCache = Collections.emptyList();
            channelByIdCache = Collections.emptyMap();
            paymentByIdCache = Collections.emptyMap();
            agentByUserIdCache = Collections.emptyMap();
            merchantByUserIdCache = Collections.emptyMap();
            dayStartByCurrency.clear();
            nextDayStartByCurrency.clear();
            baseDateByCurrency.clear();
            agentReportMap.clear();
            nowEpochSecond = 0L;
        }

        /**
         * Fix report base date for all currencies (daily/monthly/yearly).
         *
         * @param targetDate target date
         * @param currencies currency set
         */
        private void applyFixedReportDate(LocalDate targetDate, Set<String> currencies) {
            if (targetDate == null) {
                return;
            }
            for (String currency : CommonUtil.safeList(new ArrayList<>(currencies))) {
                if (currency == null || currency.isBlank()) {
                    continue;
                }
                String key = currency.trim();
                baseDateByCurrency.put(key, targetDate);
                baseDateByCurrency.put("M:" + key, targetDate);
                baseDateByCurrency.put("Y:" + key, targetDate);
                dayStartByCurrency.remove(key);
                nextDayStartByCurrency.remove(key);
            }
        }

        /**
         * Load merchant snapshot and related caches.
         */
        private void loadMerchantSnapshot() {
            log.info("loadMerchantSnapshot start");
            merchantInfoCache = listAllMerchants();
            channelInfoCache = CommonUtil.safeList(channelMapper.getAllChannels());
            paymentInfoCache = CommonUtil.safeList(paymentMapper.getAllPayments());
            agentInfoCache = CommonUtil.safeList(agentInfoMapper.getAllAgentInfo());

            channelByIdCache = channelInfoCache.stream()
                    .collect(Collectors.toMap(ChannelDto::getChannelId, Function.identity(), (a, b) -> a));
            paymentByIdCache = paymentInfoCache.stream()
                    .collect(Collectors.toMap(PaymentDto::getPaymentId, Function.identity(), (a, b) -> a));
            agentByUserIdCache = agentInfoCache.stream()
                    .collect(Collectors.toMap(AgentInfoDto::getUserId, Function.identity(), (a, b) -> a));
            merchantByUserIdCache = merchantInfoCache.stream()
                    .filter(Objects::nonNull)
                    .filter(m -> m.getUserId() != null)
                    .collect(Collectors.toMap(MerchantInfoDto::getUserId, Function.identity(), (a, b) -> a));

            // Enrich merchant data: attach agent chain, channel list, and currency list.
            attachMerchantDetails(merchantInfoCache);
            log.info("loadMerchantSnapshot end, merchantCount={}, channelCount={}, paymentCount={}, agentCount={}",
                    merchantInfoCache.size(),
                    channelInfoCache.size(),
                    paymentInfoCache.size(),
                    agentInfoCache.size());
        }

        /**
         * Load all enabled merchants.
         *
         * @return enabled merchant list
         */
        private List<MerchantInfoDto> listAllMerchants() {
            List<MerchantInfoDto> merchants = merchantInfoMapper.listEnabledMerchants();
            return merchants == null ? Collections.emptyList() : merchants;
        }

        /**
         * Resolve unique currencies from payment list.
         *
         * @return unique currency set
         */
        private Set<String> resolveAllCurrencies() {
            Set<String> currencies = new LinkedHashSet<>();
            for (PaymentDto payment : paymentInfoCache) {
                if (payment != null && payment.getCurrency() != null && !payment.getCurrency().isBlank()) {
                    currencies.add(payment.getCurrency().trim());
                }
            }
            if (currencies.isEmpty()) {
                currencies.add("US");
            }
            return currencies;
        }

        /**
         * Resolve merchant currency list with default fallback.
         *
         * @param merchant merchant info
         * @return resolved currency list
         */
        private List<String> resolveMerchantCurrencies(MerchantInfoDto merchant) {
            if (merchant.getCurrencyList() != null && !merchant.getCurrencyList().isEmpty()) {
                return merchant.getCurrencyList();
            }
            return Collections.singletonList("US");
        }

        /**
         * Resolve start-of-day epoch seconds by currency timezone.
         *
         * @param currency currency code
         * @return start-of-day epoch seconds
         */
        private long resolveDayStart(String currency) {
            return dayStartByCurrency.computeIfAbsent(currency, key -> {
                ZoneId zoneId = CommonUtil.resolveZoneIdByCurrency(key);
                LocalDate baseDate = resolveReportBaseDate(key, zoneId);
                return baseDate.atStartOfDay(zoneId).toEpochSecond();
            });
        }

        /**
         * Resolve next-day start epoch seconds by currency timezone.
         *
         * @param currency currency code
         * @return next-day start epoch seconds
         */
        private long resolveNextDayStart(String currency) {
            return nextDayStartByCurrency.computeIfAbsent(currency, key -> {
                ZoneId zoneId = CommonUtil.resolveZoneIdByCurrency(key);
                LocalDate baseDate = resolveReportBaseDate(key, zoneId);
                return baseDate.plusDays(1).atStartOfDay(zoneId).toEpochSecond();
            });
        }

        /**
         * Resolve report base date by currency timezone.
         *
         * @param currency currency code
         * @param zoneId timezone
         * @return base date for report
         */
        private LocalDate resolveReportBaseDate(String currency, ZoneId zoneId) {
            return baseDateByCurrency.computeIfAbsent(currency, key -> {
                // Use a consistent timestamp for the whole report run (fallback to system time).
                ZonedDateTime now = nowEpochSecond > 0
                        ? Instant.ofEpochSecond(nowEpochSecond).atZone(zoneId)
                        : ZonedDateTime.now(zoneId);
                // Daily report: treat 00:00:00 ~ 00:50:00 as "midnight trigger".
                return now.toLocalTime().isBefore(LocalTime.of(0, 50, 0))
                        ? now.toLocalDate().minusDays(1)
                        : now.toLocalDate();
            });
        }

        /**
         * Resolve month start epoch seconds by currency timezone.
         *
         * @param currency currency code
         * @return month start epoch seconds
         */
        private long resolveMonthStart(String currency) {
            ZoneId zoneId = CommonUtil.resolveZoneIdByCurrency(currency);
            LocalDate baseDate = resolveReportBaseDateForMonth(currency, zoneId);
            LocalDate monthStart = baseDate.withDayOfMonth(1);
            return monthStart.atStartOfDay(zoneId).toEpochSecond();
        }

        /**
         * Resolve next month start epoch seconds by currency timezone.
         *
         * @param currency currency code
         * @return next month start epoch seconds
         */
        private long resolveNextMonthStart(String currency) {
            ZoneId zoneId = CommonUtil.resolveZoneIdByCurrency(currency);
            LocalDate baseDate = resolveReportBaseDateForMonth(currency, zoneId);
            YearMonth ym = YearMonth.from(baseDate).plusMonths(1);
            return ym.atDay(1).atStartOfDay(zoneId).toEpochSecond();
        }

        /**
         * Resolve year start epoch seconds by currency timezone.
         *
         * @param currency currency code
         * @return year start epoch seconds
         */
        private long resolveYearStart(String currency) {
            ZoneId zoneId = CommonUtil.resolveZoneIdByCurrency(currency);
            LocalDate baseDate = resolveReportBaseDateForYear(currency, zoneId);
            LocalDate yearStart = LocalDate.of(baseDate.getYear(), 1, 1);
            return yearStart.atStartOfDay(zoneId).toEpochSecond();
        }

        /**
         * Resolve next year start epoch seconds by currency timezone.
         *
         * @param currency currency code
         * @return next year start epoch seconds
         */
        private long resolveNextYearStart(String currency) {
            ZoneId zoneId = CommonUtil.resolveZoneIdByCurrency(currency);
            LocalDate baseDate = resolveReportBaseDateForYear(currency, zoneId);
            LocalDate nextYearStart = LocalDate.of(baseDate.getYear() + 1, 1, 1);
            return nextYearStart.atStartOfDay(zoneId).toEpochSecond();
        }

        /**
         * Resolve month base date: only roll back on first day 00:00~00:50.
         *
         * @param currency currency code
         * @param zoneId timezone
         * @return base date for monthly report
         */
        private LocalDate resolveReportBaseDateForMonth(String currency, ZoneId zoneId) {
            return baseDateByCurrency.computeIfAbsent("M:" + currency, key -> {
                ZonedDateTime now = nowEpochSecond > 0
                        ? Instant.ofEpochSecond(nowEpochSecond).atZone(zoneId)
                        : ZonedDateTime.now(zoneId);
                if (now.getDayOfMonth() == 1 && now.toLocalTime().isBefore(LocalTime.of(0, 50, 0))) {
                    log.info("ops monthly report rollback to previous month, currency={}, now={}", currency, now);
                    return now.toLocalDate().minusMonths(1);
                }
                return now.toLocalDate();
            });
        }

        /**
         * Resolve year base date: only roll back on Jan 1 00:00~00:50.
         *
         * @param currency currency code
         * @param zoneId timezone
         * @return base date for yearly report
         */
        private LocalDate resolveReportBaseDateForYear(String currency, ZoneId zoneId) {
            return baseDateByCurrency.computeIfAbsent("Y:" + currency, key -> {
                ZonedDateTime now = nowEpochSecond > 0
                        ? Instant.ofEpochSecond(nowEpochSecond).atZone(zoneId)
                        : ZonedDateTime.now(zoneId);
                if (now.getMonthValue() == 1 && now.getDayOfMonth() == 1
                        && now.toLocalTime().isBefore(LocalTime.of(0, 50, 0))) {
                    log.info("ops yearly report rollback to previous year, currency={}, now={}", currency, now);
                    return now.toLocalDate().minusYears(1);
                }
                return now.toLocalDate();
            });
        }

        /**
         * Resolve merged currency set across all merchants.
         *
         * @return merged currency set
         */
        private Set<String> resolveMerchantCurrencySet() {
            Set<String> currencies = new LinkedHashSet<>();
            for (MerchantInfoDto merchant : merchantInfoCache) {
                if (merchant == null) {
                    continue;
                }
                currencies.addAll(resolveMerchantCurrencies(merchant));
            }
            if (currencies.isEmpty()) {
                currencies.add("US");
            }
            return currencies;
        }

        /**
         * Resolve merchant currencies filtered by support direction.
         *
         * @param merchant merchant info
         * @param isCollection true for collection, false for payout
         * @return currency set
         */
        private Set<String> resolveMerchantCurrenciesForSupport(MerchantInfoDto merchant, boolean isCollection) {
            if (merchant == null || merchant.getUserId() == null) {
                return Collections.emptySet();
            }
            if (isCollection && !CommonUtil.supportsCollection(merchant.getSupportType())) {
                return Collections.emptySet();
            }
            if (!isCollection && !CommonUtil.supportsPay(merchant.getSupportType())) {
                return Collections.emptySet();
            }
            return new LinkedHashSet<>(resolveMerchantCurrencies(merchant));
        }

        /**
         * Resolve channel currencies filtered by support direction.
         *
         * @param channel channel info
         * @param isCollection true for collection, false for payout
         * @return currency set
         */
        private Set<String> resolveChannelCurrencies(ChannelDto channel, boolean isCollection) {
            List<Long> paymentIds = CommonUtil.parseIds(channel.getPaymentIds());
            if (paymentIds.isEmpty()) {
                return Collections.emptySet();
            }
            Set<String> currencies = new LinkedHashSet<>();
            for (Long paymentId : paymentIds) {
                PaymentDto payment = paymentByIdCache.get(paymentId);
                if (payment == null || payment.getCurrency() == null || payment.getCurrency().isBlank()) {
                    continue;
                }
                if (isCollection && CommonUtil.supportsCollection(payment.getSupportType())) {
                    currencies.add(payment.getCurrency().trim());
                }
                if (!isCollection && CommonUtil.supportsPay(payment.getSupportType())) {
                    currencies.add(payment.getCurrency().trim());
                }
            }
            return currencies;
        }

        /**
         * Resolve payment currency filtered by support direction.
         *
         * @param payment payment info
         * @param isCollection true for collection, false for payout
         * @return currency set
         */
        private Set<String> resolvePaymentCurrencies(PaymentDto payment, boolean isCollection) {
            if (payment == null || payment.getPaymentId() == null) {
                return Collections.emptySet();
            }
            String currency = payment.getCurrency();
            if (currency == null || currency.isBlank()) {
                return Collections.emptySet();
            }
            if (isCollection && !CommonUtil.supportsCollection(payment.getSupportType())) {
                return Collections.emptySet();
            }
            if (!isCollection && !CommonUtil.supportsPay(payment.getSupportType())) {
                return Collections.emptySet();
            }
            return Collections.singleton(currency.trim());
        }

        /**
         * Build lookup key by id + currency.
         *
         * @param id entity id
         * @param currency currency code
         * @return lookup key
         */
        private String buildKey(Object id, String currency) {
            if (id == null || currency == null || currency.isBlank()) {
                return null;
            }
            return id + "|" + currency;
        }

        /**
         * Attach merchant channel/agent/currency details.
         *
         * @param merchants merchant list
         */
        private void attachMerchantDetails(List<MerchantInfoDto> merchants) {
            if (merchants == null || merchants.isEmpty()) {
                return;
            }
            for (MerchantInfoDto merchant : merchants) {
                if (merchant == null) {
                    continue;
                }
                List<Long> channelIds;
                if (merchant.getParentId() != null && !merchant.getParentId().isBlank()) {
                    // Merchant under agent: use agent channels and build agent chain.
                    AgentInfoDto parentAgent = agentByUserIdCache.get(merchant.getParentId());
                    channelIds = CommonUtil.parseIds(parentAgent == null ? null : parentAgent.getChannelIds());
                    List<AgentInfoDto> agentChain = MerchantServiceImpl.buildAgentChain(agentByUserIdCache, merchant.getParentId());
                    merchant.setAgentInfos(agentChain);
                } else {
                    // Direct merchant: use merchant channels and attach channel info list.
                    channelIds = CommonUtil.parseIds(merchant.getChannelIds());
                    List<ChannelDto> channelInfos = AgentServiceImpl.buildChannelListByIds(channelIds, channelByIdCache);
                    merchant.setChannelDtoList(channelInfos);
                }
                // Resolve supported currencies from channel/payment mapping.
                List<String> currencies = MerchantServiceImpl.resolveCurrenciesByChannelIds(
                        channelIds, channelByIdCache, paymentByIdCache);
                merchant.setCurrencyList(currencies);
            }
        }
    }
}
