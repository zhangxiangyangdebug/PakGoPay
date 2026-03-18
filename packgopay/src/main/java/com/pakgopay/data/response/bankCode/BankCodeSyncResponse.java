package com.pakgopay.data.response.bankCode;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class BankCodeSyncResponse {
    private String source;
    private Integer totalRows;
    private Integer insertedCount;
    private Integer updatedCount;
    private Integer invalidRowCount;
    private List<String> invalidRows;
}
