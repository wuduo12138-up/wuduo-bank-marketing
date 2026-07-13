package com.wuduo.bank.activity.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wuduo.bank.activity.domain.entity.ActivityUserProgress;
import org.apache.ibatis.annotations.Mapper;

/**
 * Activity user progress mapper interface
 */
@Mapper
public interface ActivityUserProgressMapper extends BaseMapper<ActivityUserProgress> {
}
