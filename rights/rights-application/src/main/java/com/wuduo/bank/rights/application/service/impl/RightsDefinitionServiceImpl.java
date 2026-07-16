package com.wuduo.bank.rights.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wuduo.bank.common.exception.BizException;
import com.wuduo.bank.common.model.ErrorCode;
import com.wuduo.bank.rights.api.dto.RightsDefinitionCreateRequest;
import com.wuduo.bank.rights.api.dto.RightsDefinitionResponse;
import com.wuduo.bank.rights.api.dto.RightsDefinitionUpdateRequest;
import com.wuduo.bank.rights.application.service.RightsDefinitionService;
import com.wuduo.bank.rights.domain.entity.RightsDefinition;
import com.wuduo.bank.rights.infrastructure.mapper.RightsDefinitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rights Definition Application Service Implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RightsDefinitionServiceImpl implements RightsDefinitionService {

    private static final DateTimeFormatter CODE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RightsDefinitionMapper rightsDefinitionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RightsDefinitionResponse create(RightsDefinitionCreateRequest request) {
        if (request.getTotalStock() <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "总库存必须大于0");
        }
        if (request.getValidDays() <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "有效天数必须大于0");
        }

        // Generate rights code: RGTS + yyyyMMdd + 4 random digits
        String rightsCode = "RGTS" + LocalDate.now().format(CODE_DATE_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));

        RightsDefinition definition = new RightsDefinition();
        definition.setRightsCode(rightsCode);
        definition.setName(request.getName());
        definition.setType(request.getType());
        definition.setSupplierType(request.getSupplierType());
        definition.setSupplierCode(request.getSupplierCode());
        definition.setTotalStock(request.getTotalStock());
        definition.setUsedStock(0);
        definition.setValidDays(request.getValidDays());
        definition.setStatus(1); // enabled by default
        definition.setCallbackUrl(request.getCallbackUrl());
        rightsDefinitionMapper.insert(definition);

        log.info("Created rights definition id={} code={} name={}", definition.getId(), rightsCode, request.getName());
        return toResponse(definition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RightsDefinitionResponse update(Long id, RightsDefinitionUpdateRequest request) {
        RightsDefinition definition = rightsDefinitionMapper.selectById(id);
        if (definition == null) {
            throw new BizException(ErrorCode.RIGHTS_NOT_FOUND);
        }

        if (request.getName() != null) {
            definition.setName(request.getName());
        }
        if (request.getType() != null) {
            definition.setType(request.getType());
        }
        if (request.getSupplierType() != null) {
            definition.setSupplierType(request.getSupplierType());
        }
        if (request.getSupplierCode() != null) {
            definition.setSupplierCode(request.getSupplierCode());
        }
        if (request.getTotalStock() != null) {
            if (request.getTotalStock() < definition.getUsedStock()) {
                throw new BizException(ErrorCode.BAD_REQUEST, "总库存不能小于已使用库存(" + definition.getUsedStock() + ")");
            }
            definition.setTotalStock(request.getTotalStock());
        }
        if (request.getValidDays() != null) {
            if (request.getValidDays() <= 0) {
                throw new BizException(ErrorCode.BAD_REQUEST, "有效天数必须大于0");
            }
            definition.setValidDays(request.getValidDays());
        }
        if (request.getCallbackUrl() != null) {
            definition.setCallbackUrl(request.getCallbackUrl());
        }
        rightsDefinitionMapper.updateById(definition);

        log.info("Updated rights definition id={}", id);
        return toResponse(definition);
    }

    @Override
    public RightsDefinitionResponse getById(Long id) {
        RightsDefinition definition = rightsDefinitionMapper.selectById(id);
        if (definition == null) {
            throw new BizException(ErrorCode.RIGHTS_NOT_FOUND);
        }
        return toResponse(definition);
    }

    @Override
    public List<RightsDefinitionResponse> page(Integer pageNum, Integer pageSize, Integer type) {
        LambdaQueryWrapper<RightsDefinition> wrapper = new LambdaQueryWrapper<>();
        if (type != null) {
            wrapper.eq(RightsDefinition::getType, type);
        }
        wrapper.orderByDesc(RightsDefinition::getCreateTime);

        IPage<RightsDefinition> page = rightsDefinitionMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);
        return page.getRecords().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        RightsDefinition definition = rightsDefinitionMapper.selectById(id);
        if (definition == null) {
            throw new BizException(ErrorCode.RIGHTS_NOT_FOUND);
        }
        definition.setStatus(1);
        rightsDefinitionMapper.updateById(definition);
        log.info("Enabled rights definition id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        RightsDefinition definition = rightsDefinitionMapper.selectById(id);
        if (definition == null) {
            throw new BizException(ErrorCode.RIGHTS_NOT_FOUND);
        }
        definition.setStatus(0);
        rightsDefinitionMapper.updateById(definition);
        log.info("Disabled rights definition id={}", id);
    }

    private RightsDefinitionResponse toResponse(RightsDefinition definition) {
        RightsDefinitionResponse resp = new RightsDefinitionResponse();
        resp.setId(definition.getId());
        resp.setRightsCode(definition.getRightsCode());
        resp.setName(definition.getName());
        resp.setType(definition.getType());
        resp.setSupplierType(definition.getSupplierType());
        resp.setSupplierCode(definition.getSupplierCode());
        resp.setTotalStock(definition.getTotalStock());
        resp.setUsedStock(definition.getUsedStock());
        resp.setValidDays(definition.getValidDays());
        resp.setStatus(definition.getStatus());
        return resp;
    }
}
