package com.pakgopay.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;
import com.pakgopay.common.reqeust.ExportBaseRequest;
import com.pakgopay.common.response.CommonResponse;
import com.pakgopay.service.report.ExportReportDataColumns;
import com.pakgopay.service.report.ThrowingFunction;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public class ExportFileUtils {

    public static void writeJsonError(HttpServletResponse response, ResultCode code, String msg) {
        try {
            if (response.isCommitted()) {
                log.warn("Cannot write JSON error because response is already committed. msg={}", msg);
                return;
            }
            response.reset();
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");

            CommonResponse<Void> fail = CommonResponse.fail(code, msg);
            String json = new ObjectMapper().writeValueAsString(fail);
            response.getWriter().write(json);
        } catch (Exception ex) {
            log.error("writeJsonError failed", ex);
        }
    }

    /**
     * Core export logic: paging query + multi-sheet writing
     */
    public static <REQ extends ExportBaseRequest, ROW> void exportByPagingAndSheets(
            HttpServletResponse response,
            List<List<String>> head,
            REQ request,
            ThrowingFunction<REQ, List<ROW>, PakGoPayException> pageFetcher,
            List<ExportReportDataColumns.ColumnDef<ROW>> defs, String fileName)
            throws IOException, PakGoPayException {

        int sheetNo = 1;           // Current sheet index (start from 1)
        int sheetRowCount = 0;     // Current written row count in current sheet
        boolean wroteAny = false;  // Whether any data has been written

        ExcelWriter writer = null;
        try {

            while (true) {
                // 1) Fetch one page data
                List<ROW> pageData = pageFetcher.apply(request);

                // 2) Stop if no more data
                if (pageData == null || pageData.isEmpty()) {
                    break;
                }
                // 3) Set Excel download response headers
                setExcelDownloadHeaders(response, fileName);

                wroteAny = true;

                // 4) Convert DTO list to EasyExcel dynamic rows
                List<List<String>> excelRows = toDynamicRows(pageData, defs);

                // 5) Switch to next sheet if current sheet capacity is not enough
                if (sheetRowCount + excelRows.size() > ExportReportDataColumns.EXPORT_SHEET_ROW_LIMIT) {
                    sheetNo++;
                    sheetRowCount = 0;
                }

                // 6) Write to current sheet
                if (writer == null) {
                    var os = response.getOutputStream();
                    writer = EasyExcel.write(os)
                            .head(head)
                            .autoCloseStream(false)
                            .build();
                }
                writer.write(excelRows, EasyExcel.writerSheet(sheetNo, "report-" + sheetNo).build());

                // 7) Update current sheet row count
                sheetRowCount += excelRows.size();

                // 8) If current page size is less than page size, it means this is the last page
                if (excelRows.size() < ExportReportDataColumns.EXPORT_PAGE_SIZE) {
                    break;
                }

                // 9) Move to next page
                request.setPageNo(request.getPageNo() + 1);
            }
        } catch (Exception e) {
            log.warn("writer excel failed");
            throw new PakGoPayException(ResultCode.FAIL, "writer excel failed");
        }

        // 9) If no data has been written, treat as empty result
        if (!wroteAny) {
            log.warn("data is empty");
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "data is empty");
        }
    }

    /**
     * Set Excel download headers
     */
    public static void setExcelDownloadHeaders(HttpServletResponse response, String fileName) throws IOException {
        if (response.isCommitted()) {
            log.warn("response is commit");
            return;
        }
        log.warn("setExcelDownloadHeaders start");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
    }

    /**
     * Parse and validate merchant export columns (must use whitelist)
     */
    public static <ROW> ColumnParseResult<ROW> parseColumns(
            ExportBaseRequest req,
            Map<String, ExportReportDataColumns.ColumnDef<ROW>> allowedMap)
            throws PakGoPayException {

        // 1) Validate columns
        if (req.getColumns() == null || req.getColumns().isEmpty()) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, "columns is empty");
        }

        // 2) Parse columns by frontend order
        List<ExportReportDataColumns.ColumnDef<ROW>> defs = new ArrayList<>();
        List<List<String>> head = new ArrayList<>();

        for (ExportBaseRequest.ExportCol col : req.getColumns()) {
            var def = allowedMap.get(col.getKey());
            if (def == null) {
                throw new PakGoPayException(ResultCode.INVALID_PARAMS, "not support column: " + col.getKey());
            }

            defs.add(def);

            // Use frontend title first, otherwise fallback to backend default title
            String title = (col.getTitle() != null && !col.getTitle().isBlank())
                    ? col.getTitle()
                    : def.defaultTitle();

            // EasyExcel dynamic header format: List<List<String>>
            head.add(Collections.singletonList(title));
        }

        return new ColumnParseResult<>(defs, head);
    }

    /**
     * Convert DTO list to EasyExcel dynamic row format (List<List<String>>)
     */
    public static <ROW> List<List<String>> toDynamicRows(
            List<ROW> list,
            List<ExportReportDataColumns.ColumnDef<ROW>> defs) {

        return list.stream()
                .map(r -> defs.stream()
                        .map(d -> d.getter().apply(r))
                        .toList())
                .toList();
    }


    /**
     * Column parse result holder
     */
    public static class ColumnParseResult<T> {
        private final List<ExportReportDataColumns.ColumnDef<T>> defs;
        private final List<List<String>> head;

        public ColumnParseResult(List<ExportReportDataColumns.ColumnDef<T>> defs, List<List<String>> head) {
            this.defs = defs;
            this.head = head;
        }

        public List<ExportReportDataColumns.ColumnDef<T>> getDefs() {
            return defs;
        }

        public List<List<String>> getHead() {
            return head;
        }
    }
}
