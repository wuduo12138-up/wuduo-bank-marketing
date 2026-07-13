package com.wuduo.bank.activity.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wuduo.bank.activity.domain.entity.ActivityParticipation;
import org.apache.ibatis.annotations.Mapper;

/**
 * Activity participation mapper interface
 */
@Mapper
public interface ActivityParticipationMapper extends BaseMapper<ActivityParticipation> {
}
