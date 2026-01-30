package com.pakgopay.timer.data;

import java.time.LocalDate;

public class OpsContext {
    /** Report period. */
    public OpsPeriod period;
    /** Report date (daily). */
    public LocalDate reportDate;
    /** Report month (yyyy-MM). */
    public String reportMonth;
    /** Report year (yyyy). */
    public String reportYear;
    /** Currency code. */
    public String currency;
}
