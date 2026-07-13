package com.wuduo.bank.point.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wuduo.bank.point.domain.entity.PointAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * Point account mapper
 */
@Mapper
public interface PointAccountMapper extends BaseMapper<PointAccount> {
}
