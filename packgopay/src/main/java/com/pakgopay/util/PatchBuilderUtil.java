package com.pakgopay.util;

import com.pakgopay.common.enums.ResultCode;
import com.pakgopay.common.exception.PakGoPayException;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class PatchBuilderUtil<REQ, DTO> {

    private final REQ req;
    private final DTO dto;

    private int updated = 0;

    /**
     * Skip flag for next chain segment.
     * If true, the next patch method will be skipped once, then reset to false.
     */
    private boolean skipOnce = false;

    private PatchBuilderUtil(REQ req, DTO dto) {
        this.req = req;
        this.dto = dto;
    }

    public static <REQ> FromStep<REQ> from(REQ req) {
        return new FromStep<>(req);
    }

    public static final class FromStep<REQ> {
        private final REQ req;
        private FromStep(REQ req) { this.req = req; }
        public <DTO> PatchBuilderUtil<REQ, DTO> to(DTO dto) {
            return new PatchBuilderUtil<>(req, dto);
        }
    }

    public DTO dto() { return dto; }
    public int updatedCount() { return updated; }

    /** Enable skipping the next patch method if condition is false */
    public PatchBuilderUtil<REQ, DTO> ifTrue(boolean condition) {
        this.skipOnce = !condition;
        return this;
    }

    /** Enable skipping the next patch method if condition is true */
    public PatchBuilderUtil<REQ, DTO> ifFalse(boolean condition) {
        this.skipOnce = condition;
        return this;
    }

    private boolean shouldSkipOnce() {
        if (skipOnce) {
            skipOnce = false;
            return true;
        }
        return false;
    }

    private PakGoPayException invalid(String msg) {
        return new PakGoPayException(ResultCode.INVALID_PARAMS, msg);
    }

    // ---------------------------
    // Required setters (ADD)
    // ---------------------------

    /** Required string: trim + not blank */
    public PatchBuilderUtil<REQ, DTO> reqStr(String field, Supplier<String> getter, Consumer<String> setter) throws PakGoPayException {
        if (shouldSkipOnce()) return this;

        String v = getter.get();
        if (v == null || v.trim().isEmpty()) {
            throw invalid(field + " is empty");
        }
        setter.accept(v.trim());
        updated++;
        return this;
    }

    /** Required object: not null */
    public <T> PatchBuilderUtil<REQ, DTO> reqObj(String field, Supplier<T> getter, Consumer<T> setter) throws PakGoPayException {
        if (shouldSkipOnce()) return this;

        T v = getter.get();
        if (v == null) {
            throw invalid(field + " is null");
        }
        setter.accept(v);
        updated++;
        return this;
    }

    // ---------------------------
    // Optional setters (EDIT or optional fields)
    // ---------------------------

    /** Optional string: trim + not blank */
    public PatchBuilderUtil<REQ, DTO> str(Supplier<String> getter, Consumer<String> setter) {
        if (shouldSkipOnce()) return this;

        String v = getter.get();
        if (v != null) {
            String t = v.trim();
            if (!t.isEmpty()) {
                setter.accept(t);
                updated++;
            }
        }
        return this;
    }

    /** Optional object: not null */
    public <T> PatchBuilderUtil<REQ, DTO> obj(Supplier<T> getter, Consumer<T> setter) {
        if (shouldSkipOnce()) return this;

        T v = getter.get();
        if (v != null) {
            setter.accept(v);
            updated++;
        }
        return this;
    }

    /**
     * List -> CSV string
     * - list == null : do not update
     * - empty list  : update with ""
     */
    public PatchBuilderUtil<REQ, DTO> ids(Supplier<? extends List<?>> getter, Consumer<String> setter) {
        if (shouldSkipOnce()) return this;

        List<?> list = getter.get();
        if (list == null) return this;

        String csv = list.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
        setter.accept(csv); // empty -> ""
        updated++;
        return this;
    }

    // ---------------------------
    // Validation helpers
    // ---------------------------

    public PatchBuilderUtil<REQ, DTO> require(boolean condition, String message) throws PakGoPayException {
        if (shouldSkipOnce()) return this;
        if (!condition) throw invalid(message);
        return this;
    }

    public DTO build() {
        return dto;
    }

    public DTO throwIfNoUpdate(PakGoPayException ex) throws PakGoPayException {
        if (updated == 0) {
            throw ex;
        }
        return dto;
    }

    public static Long parseRequiredLong(String value, String fieldName) throws PakGoPayException {
        if (value == null || value.isBlank()) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, fieldName + " is blank");
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new PakGoPayException(ResultCode.INVALID_PARAMS, fieldName + " is invalid");
        }
    }
}
