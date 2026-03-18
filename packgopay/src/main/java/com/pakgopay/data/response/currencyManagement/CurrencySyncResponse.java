package com.pakgopay.data.response.currencyManagement;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CurrencySyncResponse {
    private String source;
    private Integer totalRows;
    private Integer insertedCount;
    private Integer skippedExistingCount;
    private Integer invalidRowCount;
    private List<String> skippedCurrencies;
    private List<String> invalidRows;
}
