package com.pakgopay.mapper.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class BankCodeSyncExcelRow {
    @ExcelProperty(index = 0)
    private String id;

    @ExcelProperty(index = 1)
    private String bankName;

    @ExcelProperty(index = 2)
    private String bankCode;

    @ExcelProperty(index = 3)
    private String country;

    @ExcelProperty(index = 4)
    private String currencyName;

    @ExcelProperty(index = 5)
    private String currencyCode;
}
