package com.wuduo.bank.point.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wuduo.bank.point.domain.entity.PointRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * Point record mapper
 */
@Mapper
public interface PointRecordMapper extends BaseMapper<PointRecord> {
}
