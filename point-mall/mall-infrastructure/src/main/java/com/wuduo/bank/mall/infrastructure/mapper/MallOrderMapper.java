package com.wuduo.bank.mall.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wuduo.bank.mall.domain.entity.MallOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mall order mapper
 */
@Mapper
public interface MallOrderMapper extends BaseMapper<MallOrder> {
}
