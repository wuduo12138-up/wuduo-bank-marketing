package com.wuduo.bank.mall.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wuduo.bank.mall.domain.entity.MallProduct;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mall product mapper
 */
@Mapper
public interface MallProductMapper extends BaseMapper<MallProduct> {
}
