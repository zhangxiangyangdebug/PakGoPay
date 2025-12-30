package com.pakgopay.mapper;


import com.pakgopay.mapper.dto.MerchantInfoDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantInfoMapper {

    /**
     * 1、findByUserId 通过 userId 查询
     */
    MerchantInfoDto findByUserId(@Param("userId") String userId);

    /**
     * 2、saveData 插入保存数据
     * @return 影响行数
     */
    int saveData(MerchantInfoDto dto);

    /** xiaoyou 3、upDatePayWhiteIpsByUserId：通过指定 userId 更新 pay_white_ips */
    int upDatePayWhiteIpsByUserId(@Param("userId") String userId,
                                  @Param("payWhiteIps") String payWhiteIps);

    /** xiaoyou 4、upDateColWhiteIpsByUserId：通过指定 userId 更新 col_white_ips */
    int upDateColWhiteIpsByUserId(@Param("userId") String userId,
                                  @Param("colWhiteIps") String colWhiteIps);

    /**
     * 5、getEnableStatus 通过指定 userId 获取 status
     */
    Integer getEnableStatus(@Param("userId") String userId);
}
