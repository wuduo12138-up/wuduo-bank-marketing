package com.wuduo.bank.activity.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wuduo.bank.activity.domain.entity.ActivityBudgetLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * Activity budget log mapper interface
 */
@Mapper
public interface ActivityBudgetLogMapper extends BaseMapper<ActivityBudgetLog> {
}
