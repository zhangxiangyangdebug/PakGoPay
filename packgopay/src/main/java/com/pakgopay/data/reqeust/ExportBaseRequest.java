package com.pakgopay.data.reqeust;

import lombok.Data;

import java.util.List;

@Data
public class ExportBaseRequest extends BaseRequest{
    /**
     * Page number (start from 1)
     */
    private Integer pageNo;

    /**
     * Page size
     */
    private Integer pageSize;

    /**
     * export table title
     */
    private List<ExportBaseRequest.ExportCol> columns;

    /**
     * default page size 10
     * @return page size
     */
    public Integer getPageSize() {
        if (pageSize == null) {
            return 10;
        }
        return pageSize;
    }

    /**
     * default page no 1
     * @return page no
     */
    public Integer getPageNo() {
        if (pageNo == null) {
            return 1;
        }
        return pageNo;
    }

    @Data
    public static class ExportCol {
        private String key;   // server white list key
        private String title; // Excel table title
    }
}
