package com.pakgopay.timer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantReportTask {

    @Transactional
    public void doHourlyReport() {
    }
}
