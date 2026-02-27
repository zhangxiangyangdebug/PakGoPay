package com.pakgopay.data.reqeust.transaction;

import com.pakgopay.data.reqeust.ExportBaseRequest;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Data
@Slf4j
public class OrderQueryRequest extends ExportBaseRequest {
    /** merchant user id */
    private String merchantUserId;

    /** system transaction no */
    private String transactionNo;

    /** merchant order no */
    private String merchantOrderNo;

    /** currency code */
    private String currency;

    /** order status */
    private String orderStatus;

    /** order type: 1-system, 2-manual */
    private Integer orderType;

    /** order amount */
    private BigDecimal amount;

    /** channel id */
    private Long channelId;

    /** create_time >= startTime (unix seconds) */
    @NotNull(message = "startTime is null")
    private Long startTime;

    /** create_time < endTime (unix seconds) */
    @NotNull(message = "endTime is null")
    private Long endTime;

    @AssertTrue(message = "startTime must be less than endTime")
    public boolean isTimeRangeOrderValid() {
        if (startTime == null || endTime == null) {
            return true;
        }
        boolean valid = startTime < endTime;
        if (!valid) {
            ZonedDateTime start = Instant.ofEpochSecond(startTime).atZone(ZoneOffset.UTC);
            ZonedDateTime end = Instant.ofEpochSecond(endTime).atZone(ZoneOffset.UTC);
            log.error("Order query time range invalid: startTime must be less than endTime, startTime={} ({} UTC), endTime={} ({} UTC)",
                    startTime, start, endTime, end);
        }
        return valid;
    }

    @AssertTrue(message = "time range cannot exceed 6 months")
    public boolean isTimeRangeWithinSixMonths() {
        if (startTime == null || endTime == null) {
            return true;
        }
        ZonedDateTime start = Instant.ofEpochSecond(startTime).atZone(ZoneOffset.UTC);
        ZonedDateTime maxEndExclusive = start.plusMonths(6);
        ZonedDateTime end = Instant.ofEpochSecond(endTime).atZone(ZoneOffset.UTC);
        boolean valid = !end.isAfter(maxEndExclusive);
        if (!valid) {
            log.error("Order query time range exceeds 6 months(UTC calendar month): startTime={} ({} UTC), endTime={} ({} UTC), maxEndExclusive={} ({} UTC)",
                    startTime, start, endTime, end, maxEndExclusive.toEpochSecond(), maxEndExclusive);
        }
        return valid;
    }

    @AssertTrue(message = "pageSize cannot exceed 200")
    public boolean isPageSizeValid() {
        Integer size = getPageSize();
        return size == null || size <= 200;
    }
}
