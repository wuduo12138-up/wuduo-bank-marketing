package com.wuduo.bank.activity.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wuduo.bank.activity.domain.entity.ActivityVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * Activity version mapper
 */
@Mapper
public interface ActivityVersionMapper extends BaseMapper<ActivityVersion> {
}
