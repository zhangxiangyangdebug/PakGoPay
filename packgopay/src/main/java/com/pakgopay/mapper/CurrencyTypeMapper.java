package com.pakgopay.mapper;

import com.pakgopay.mapper.dto.CurrencyTypeDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CurrencyTypeMapper {
    List<CurrencyTypeDTO> getAllCurrencyType();

    List<CurrencyTypeDTO> getCurrencyById(Integer id);

    Integer addNewCurrency(CurrencyTypeDTO currencyTypeDTO);
}
