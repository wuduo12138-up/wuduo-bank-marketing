package com.wuduo.bank.activity.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wuduo.bank.activity.domain.entity.Activity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Activity mapper interface
 */
@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {
}
