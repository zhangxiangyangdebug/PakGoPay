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
    private int updated;

    private PatchBuilderUtil(REQ req, DTO dto) {
        this.req = req;
        this.dto = dto;
    }

    public static <REQ> FromStep<REQ> from(REQ req) {
        return new FromStep<>(req);
    }

    public static final class FromStep<REQ> {
        private final REQ req;

        private FromStep(REQ req) {
            this.req = req;
        }

        public <DTO> PatchBuilderUtil<REQ, DTO> to(DTO dto) {
            return new PatchBuilderUtil<>(req, dto);
        }
    }

    public DTO dto() {
        return dto;
    }

    public int updatedCount() {
        return updated;
    }

    /** String: trim + not blank */
    public PatchBuilderUtil<REQ, DTO> str(Supplier<String> getter, Consumer<String> setter) {
        String value = getter.get();
        if (value != null) {
            String v = value.trim();
            if (!v.isEmpty()) {
                setter.accept(v);
                updated++;
            }
        }
        return this;
    }

    /** Any object: not null */
    public <T> PatchBuilderUtil<REQ, DTO> obj(Supplier<T> getter, Consumer<T> setter) {
        T value = getter.get();
        if (value != null) {
            setter.accept(value);
            updated++;
        }
        return this;
    }

    /**
     * List -> CSV string, allow clearing:
     * - list == null: do not update
     * - list is empty: update with ""
     */
    public PatchBuilderUtil<REQ, DTO> ids(Supplier<? extends List<?>> getter, Consumer<String> setter) {
        List<?> list = getter.get();
        if (list != null) {
            String v = list.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .collect(Collectors.joining(","));
            setter.accept(v); // empty list -> ""
            updated++;
        }
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

