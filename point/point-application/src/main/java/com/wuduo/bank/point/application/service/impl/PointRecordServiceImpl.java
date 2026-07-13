package com.wuduo.bank.point.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wuduo.bank.common.model.PageResult;
import com.wuduo.bank.point.api.dto.PointRecordResponse;
import com.wuduo.bank.point.application.service.PointRecordService;
import com.wuduo.bank.point.domain.entity.PointRecord;
import com.wuduo.bank.point.infrastructure.mapper.PointRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Point record application service implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointRecordServiceImpl implements PointRecordService {

    private final PointRecordMapper pointRecordMapper;

    @Override
    public PageResult<PointRecordResponse> pageByUserId(Long userId, Integer page, Integer pageSize) {
        Page<PointRecord> pageRequest = new Page<>(page, pageSize);
        LambdaQueryWrapper<PointRecord> wrapper = new LambdaQueryWrapper<PointRecord>()
                .eq(PointRecord::getUserId, userId)
                .orderByDesc(PointRecord::getCreatedAt);

        Page<PointRecord> result = pointRecordMapper.selectPage(pageRequest, wrapper);

        PageResult<PointRecordResponse> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(page);
        pageResult.setPageSize(pageSize);
        pageResult.setRecords(result.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList()));
        return pageResult;
    }

    private PointRecordResponse toResponse(PointRecord record) {
        PointRecordResponse response = new PointRecordResponse();
        response.setId(record.getId());
        response.setRecordNo(record.getRecordNo());
        response.setUserId(record.getUserId());
        response.setPointAmount(record.getPointAmount());
        response.setType(record.getType());
        response.setBizSource(record.getBizSource());
        response.setBizNo(record.getBizNo());
        response.setExpireTime(record.getExpireTime());
        response.setStatus(record.getStatus());
        response.setUsedAmount(record.getUsedAmount());
        response.setRemark(record.getRemark());
        response.setCreatedAt(record.getCreatedAt());
        return response;
    }
}
