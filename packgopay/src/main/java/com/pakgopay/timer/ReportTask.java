package com.pakgopay.timer;

import com.pakgopay.common.constant.CommonConstant;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.enums.TransactionStatus;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.mapper.*;
import com.pakgopay.mapper.dto.*;
import com.pakgopay.service.impl.AgentServiceImpl;
import com.pakgopay.service.impl.MerchantServiceImpl;
import com.pakgopay.util.CommonUtil;
import com.pakgopay.util.PatchBuilderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;
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

    private final ResolveHelper resolveHelper = new ResolveHelper();
    private final ComputeHelper computeHelper = new ComputeHelper();
    private final FillHelper fillHelper = new FillHelper();
    private final PersistHelper persistHelper = new PersistHelper();
    private final AgentHelper agentHelper = new AgentHelper();

    private List<MerchantInfoDto> merchantInfoCache = Collections.emptyList();
    private List<ChannelDto> channelInfoCache = Collections.emptyList();
    private List<PaymentDto> paymentInfoCache = Collections.emptyList();
    private List<AgentInfoDto> agentInfoCache = Collections.emptyList();
    private Map<Long, ChannelDto> channelByIdCache = Collections.emptyMap();
    private Map<Long, PaymentDto> paymentByIdCache = Collections.emptyMap();
    private Map<String, AgentInfoDto> agentByUserIdCache = Collections.emptyMap();
    private Map<String, Long> dayStartByCurrency = new HashMap<>();
    private Map<String, Long> nextDayStartByCurrency = new HashMap<>();
    private long nowEpochSecond = 0L;
    private Map<String, AgentReportDto> agentReportMap = new HashMap<>();

    /**
     * Execute hourly report aggregation.
     */
    public void doHourlyReport() {
        log.info("doHourlyReport start");
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
        // Build remaining report types.
        reportChannel();
        reportPayment();
        reportCurrency();

        log.info("doHourlyReport end, merchantCount={}", merchantInfoCache.size());
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
                collectionOrderMapper::listMerchantReportStats,
                (dto, currency) -> resolveHelper.buildKey(dto == null ? null : dto.getUserId(), currency));
        Map<String, MerchantReportDto> payoutStats = computeHelper.loadStatsByCurrency(
                currencies,
                payOrderMapper::listMerchantReportStats,
                (dto, currency) -> resolveHelper.buildKey(dto == null ? null : dto.getUserId(), currency));

        // Process collection and payout reports for each merchant/currency.
        computeHelper.processDualSupportReports(
                merchantInfoCache,
                resolveHelper::resolveMerchantCurrenciesForSupport,
                (merchant, currency, isCollection) -> computeHelper.applyMerchantReport(
                        merchant, currency, isCollection, collectionStats, payoutStats));
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
                collectionOrderMapper::listChannelReportStats,
                (dto, currency) -> resolveHelper.buildKey(dto == null ? null : dto.getChannelId(), currency));
        Map<String, ChannelReportDto> payoutStats = computeHelper.loadStatsByCurrency(
                currencies,
                payOrderMapper::listChannelReportStats,
                (dto, currency) -> resolveHelper.buildKey(dto == null ? null : dto.getChannelId(), currency));

        // Process channel reports for collection and payout directions.
        computeHelper.processDualSupportReports(
                channelInfoCache,
                resolveHelper::resolveChannelCurrencies,
                (channel, currency, isCollection) -> computeHelper.applyChannelReport(
                        channel, currency, isCollection, collectionStats, payoutStats));
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
                collectionOrderMapper::listPaymentReportStats,
                (dto, currency) -> resolveHelper.buildKey(dto == null ? null : dto.getPaymentId(), currency));
        Map<String, PaymentReportDto> payoutStats = computeHelper.loadStatsByCurrency(
                currencies,
                payOrderMapper::listPaymentReportStats,
                (dto, currency) -> resolveHelper.buildKey(dto == null ? null : dto.getPaymentId(), currency));

        // Process payment reports for collection and payout directions.
        computeHelper.processDualSupportReports(
                paymentInfoCache,
                resolveHelper::resolvePaymentCurrencies,
                (payment, currency, isCollection) -> computeHelper.applyPaymentReport(
                        payment, currency, isCollection, collectionStats, payoutStats));
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
                collectionOrderMapper::listCurrencyReportStats,
                (dto, currency) -> currency);
        Map<String, CurrencyReportDto> payoutStats = computeHelper.loadStatsByCurrency(
                currencies,
                payOrderMapper::listCurrencyReportStats,
                (dto, currency) -> currency);

        // Process currency reports for collection and payout directions.
        computeHelper.processDualSupportReports(
                new ArrayList<>(currencies),
                (currency, isCollection) -> Collections.singleton(currency),
                (currency, reportCurrency, isCollection) -> computeHelper.applyCurrencyReport(
                        reportCurrency, isCollection, collectionStats, payoutStats));
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
    private interface SupportReportApplier<E> {
        /**
         * Apply report generation for an entity and currency.
         *
         * @param entity entity instance
         * @param currency currency code
         * @param isCollection true for collection, false for payout
         */
        void apply(E entity, String currency, boolean isCollection);
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
        private void applyMerchantReport(MerchantInfoDto merchant,
                                         String currency,
                                         boolean isCollection,
                                         Map<String, MerchantReportDto> collectionStats,
                                         Map<String, MerchantReportDto> payoutStats) {
            if (merchant == null || merchant.getUserId() == null) {
                return;
            }
            long dayStart = resolveHelper.resolveDayStart(currency);
            int orderType = isCollection ? CommonConstant.SUPPORT_TYPE_COLLECTION : CommonConstant.SUPPORT_TYPE_PAY;
            Map<String, MerchantReportDto> stats = isCollection ? collectionStats : payoutStats;
            MerchantReportDto report = stats.get(merchant.getUserId() + "|" + currency);
            MerchantReportDto dto = fillHelper.fillReportDefaults(report, merchant, currency, orderType, dayStart);
            persistHelper.upsertMerchantReport(dto);
            agentHelper.accumulateAgentReport(merchant, dto, currency, orderType, dayStart);
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
        private void applyChannelReport(ChannelDto channel,
                                        String currency,
                                        boolean isCollection,
                                        Map<String, ChannelReportDto> collectionStats,
                                        Map<String, ChannelReportDto> payoutStats) {
            if (channel == null || channel.getChannelId() == null) {
                return;
            }
            long dayStart = resolveHelper.resolveDayStart(currency);
            int orderType = isCollection ? CommonConstant.SUPPORT_TYPE_COLLECTION : CommonConstant.SUPPORT_TYPE_PAY;
            Map<String, ChannelReportDto> stats = isCollection ? collectionStats : payoutStats;
            ChannelReportDto report = stats.get(channel.getChannelId() + "|" + currency);
            ChannelReportDto dto = fillHelper.fillChannelReportDefaults(report, channel, currency, orderType, dayStart);
            persistHelper.upsertChannelReport(dto);
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
        private void applyPaymentReport(PaymentDto payment,
                                        String currency,
                                        boolean isCollection,
                                        Map<String, PaymentReportDto> collectionStats,
                                        Map<String, PaymentReportDto> payoutStats) {
            if (payment == null || payment.getPaymentId() == null) {
                return;
            }
            long dayStart = resolveHelper.resolveDayStart(currency);
            int orderType = isCollection ? CommonConstant.SUPPORT_TYPE_COLLECTION : CommonConstant.SUPPORT_TYPE_PAY;
            Map<String, PaymentReportDto> stats = isCollection ? collectionStats : payoutStats;
            PaymentReportDto report = stats.get(payment.getPaymentId() + "|" + currency);
            PaymentReportDto dto = fillHelper.fillPaymentReportDefaults(report, payment, currency, orderType, dayStart);
            persistHelper.upsertPaymentReport(dto);
        }

        /**
         * Apply currency report aggregation and persistence.
         *
         * @param currency currency code
         * @param isCollection true for collection, false for payout
         * @param collectionStats collection stats map
         * @param payoutStats payout stats map
         */
        private void applyCurrencyReport(String currency,
                                         boolean isCollection,
                                         Map<String, CurrencyReportDto> collectionStats,
                                         Map<String, CurrencyReportDto> payoutStats) {
            long dayStart = resolveHelper.resolveDayStart(currency);
            int orderType = isCollection ? CommonConstant.SUPPORT_TYPE_COLLECTION : CommonConstant.SUPPORT_TYPE_PAY;
            Map<String, CurrencyReportDto> stats = isCollection ? collectionStats : payoutStats;
            CurrencyReportDto report = stats.get(currency);
            CurrencyReportDto dto = fillHelper.fillCurrencyReportDefaults(report, currency, orderType, dayStart);
            persistHelper.upsertCurrencyReport(dto);
        }

        /**
         * Process entities for collection/payout reports using a shared template.
         *
         * @param entities entity list
         * @param currencyResolver currency resolver
         * @param applier report applier
         */
        private <E> void processDualSupportReports(
                List<E> entities,
                SupportCurrencyResolver<E> currencyResolver,
                SupportReportApplier<E> applier) {
            log.info("processDualSupportReports start, entityCount={}", entities == null ? 0 : entities.size());
            for (E entity : CommonUtil.safeList(entities)) {
                for (String currency : safeSet(currencyResolver.resolve(entity, true))) {
                    applier.apply(entity, currency, true);
                }
                for (String currency : safeSet(currencyResolver.resolve(entity, false))) {
                    applier.apply(entity, currency, false);
                }
            }
            log.info("processDualSupportReports end");
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
                ReportStatsLoader<T> loader,
                BiFunction<T, String, String> keyFn) {
            Map<String, T> stats = new HashMap<>();
            if (currencies == null || currencies.isEmpty()) {
                return stats;
            }
            String successStatus = TransactionStatus.SUCCESS.getCode().toString();
            for (String currency : currencies) {
                long dayStart = resolveHelper.resolveDayStart(currency);
                long nextDayStart = resolveHelper.resolveNextDayStart(currency);
                List<T> list = loader.load(currency, dayStart, nextDayStart, successStatus);
                for (T dto : CommonUtil.safeList(list)) {
                    if (dto == null) {
                        continue;
                    }
                    String key = keyFn.apply(dto, currency);
                    if (key == null) {
                        continue;
                    }
                    stats.put(key, dto);
                }
            }
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
        /**
         * Upsert merchant report.
         *
         * @param dto report dto
         */
        private void upsertMerchantReport(MerchantReportDto dto) {
            upsertReport(dto, merchantReportMapper::update, merchantReportMapper::insert, "merchantReport");
        }

        /**
         * Upsert channel report.
         *
         * @param dto report dto
         */
        private void upsertChannelReport(ChannelReportDto dto) {
            upsertReport(dto, channelReportMapper::update, channelReportMapper::insert, "channelReport");
        }

        /**
         * Upsert payment report.
         *
         * @param dto report dto
         */
        private void upsertPaymentReport(PaymentReportDto dto) {
            upsertReport(dto, paymentReportMapper::update, paymentReportMapper::insert, "paymentReport");
        }

        /**
         * Upsert currency report.
         *
         * @param dto report dto
         */
        private void upsertCurrencyReport(CurrencyReportDto dto) {
            upsertReport(dto, currencyReportMapper::update, currencyReportMapper::insert, "currencyReport");
        }

        /**
         * Update then insert for a report dto.
         *
         * @param dto report dto
         * @param updateFn update function
         * @param insertFn insert function
         * @param reportName report name
         */
        private <T> void upsertReport(
                T dto,
                ToIntFunction<T> updateFn,
                ToIntFunction<T> insertFn,
                String reportName) {
            if (dto == null) {
                return;
            }
            try {
                int updated = updateFn.applyAsInt(dto);
                if (updated <= 0) {
                    int inserted = insertFn.applyAsInt(dto);
                    if (inserted <= 0) {
                        throw new PakGoPayException(ResultCode.DATA_BASE_ERROR, reportName + " insert failed");
                    }
                }
            } catch (Exception e) {
                log.error("upsert{} failed, message {}", reportName, e.getMessage());
                throw e;
            }
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
            for (AgentReportDto dto : agentReportMap.values()) {
                persistHelper.upsertReport(dto, agentReportMapper::update, agentReportMapper::insert, "agentReport");
            }
            log.info("upsertAgentReports end");
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
            dayStartByCurrency.clear();
            nextDayStartByCurrency.clear();
            agentReportMap.clear();
            nowEpochSecond = 0L;
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
                return LocalDate.now(zoneId).atStartOfDay(zoneId).toEpochSecond();
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
                return LocalDate.now(zoneId).plusDays(1).atStartOfDay(zoneId).toEpochSecond();
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
