package com.wuduo.bank.rights.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wuduo.bank.common.exception.BizException;
import com.wuduo.bank.common.model.ErrorCode;
import com.wuduo.bank.rights.api.dto.RightsInstanceResponse;
import com.wuduo.bank.rights.api.dto.RightsIssueRequest;
import com.wuduo.bank.rights.api.dto.RightsIssueResponse;
import com.wuduo.bank.rights.api.enums.RightsInstanceStatus;
import com.wuduo.bank.rights.application.service.RightsIssueService;
import com.wuduo.bank.rights.application.strategy.SupplierContext;
import com.wuduo.bank.rights.application.strategy.SupplierStrategy;
import com.wuduo.bank.rights.domain.entity.RightsDefinition;
import com.wuduo.bank.rights.domain.entity.RightsInstance;
import com.wuduo.bank.rights.domain.entity.RightsIssueLog;
import com.wuduo.bank.rights.infrastructure.mapper.RightsDefinitionMapper;
import com.wuduo.bank.rights.infrastructure.mapper.RightsInstanceMapper;
import com.wuduo.bank.rights.infrastructure.mapper.RightsIssueLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rights Issue Application Service Implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RightsIssueServiceImpl implements RightsIssueService {

    private static final DateTimeFormatter INSTANCE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RightsDefinitionMapper rightsDefinitionMapper;
    private final RightsInstanceMapper rightsInstanceMapper;
    private final RightsIssueLogMapper rightsIssueLogMapper;
    private final SupplierContext supplierContext;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RightsIssueResponse issue(RightsIssueRequest request) {
        // 1. Idempotency check: if sourceNo exists, return existing instance
        if (request.getSourceNo() != null && !request.getSourceNo().isBlank()) {
            RightsInstance existing = rightsInstanceMapper.selectOne(
                    new LambdaQueryWrapper<RightsInstance>()
                            .eq(RightsInstance::getSourceNo, request.getSourceNo())
                            .last("LIMIT 1"));
            if (existing != null) {
                log.info("Duplicate issue request, returning existing instance: instanceNo={}", existing.getInstanceNo());
                return toIssueResponse(existing);
            }
        }

        // 2. Find rights definition
        RightsDefinition definition = rightsDefinitionMapper.selectOne(
                new LambdaQueryWrapper<RightsDefinition>()
                        .eq(RightsDefinition::getRightsCode, request.getRightsCode()));
        if (definition == null) {
            throw new BizException(ErrorCode.RIGHTS_NOT_FOUND);
        }

        // 3. Check definition is enabled
        if (definition.getStatus() != 1) {
            throw new BizException(ErrorCode.RIGHTS_DEFINITION_DISABLED);
        }

        // 4. Stock pre-deduction (atomic)
        boolean stockDeducted = deductStock(definition);
        if (!stockDeducted) {
            throw new BizException(ErrorCode.RIGHTS_STOCK_INSUFFICIENT);
        }

        // 5. Generate instance number
        String instanceNo = "R" + LocalDateTime.now().format(INSTANCE_DATE_FMT)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));

        // 6. Calculate expire time
        LocalDateTime expireTime = LocalDateTime.now().plusDays(definition.getValidDays());

        // 7. Save instance
        RightsInstance instance = new RightsInstance();
        instance.setInstanceNo(instanceNo);
        instance.setRightsCode(request.getRightsCode());
        instance.setUserId(request.getUserId());
        instance.setSourceType(request.getSourceType());
        instance.setSourceNo(request.getSourceNo());
        instance.setStatus(RightsInstanceStatus.PENDING_ACTIVATE.getCode());
        instance.setExpireTime(expireTime);
        rightsInstanceMapper.insert(instance);

        // 8. Call supplier
        boolean supplierSuccess = true;
        String supplierOrderNo = null;
        String errorMsg = null;
        int retryCount = 0;

        try {
            SupplierStrategy strategy = supplierContext.getStrategy(definition.getSupplierType());
            supplierOrderNo = strategy.issue(definition, instance);
            if (supplierOrderNo != null) {
                instance.setSupplierOrderNo(supplierOrderNo);
                rightsInstanceMapper.updateById(instance);
            }
        } catch (Exception e) {
            supplierSuccess = false;
            errorMsg = e.getMessage();
            log.error("Supplier call failed for instanceNo={}: {}", instanceNo, errorMsg);
        }

        // 9. Record issue log
        RightsIssueLog issueLog = new RightsIssueLog();
        issueLog.setInstanceNo(instanceNo);
        issueLog.setRightsCode(request.getRightsCode());
        issueLog.setUserId(request.getUserId());
        issueLog.setSourceType(request.getSourceType());
        issueLog.setSourceNo(request.getSourceNo());
        issueLog.setOperationType(1); // 1=发放
        issueLog.setOperationResult(supplierSuccess ? 1 : 0);
        issueLog.setRetryCount(retryCount);
        issueLog.setErrorMsg(errorMsg);
        rightsIssueLogMapper.insert(issueLog);

        log.info("Issued rights instanceNo={} rightsCode={} userId={} supplierSuccess={}",
                instanceNo, request.getRightsCode(), request.getUserId(), supplierSuccess);
        return toIssueResponse(instance);
    }

    @Override
    public RightsInstanceResponse getByInstanceNo(String instanceNo) {
        RightsInstance instance = rightsInstanceMapper.selectOne(
                new LambdaQueryWrapper<RightsInstance>()
                        .eq(RightsInstance::getInstanceNo, instanceNo));
        if (instance == null) {
            throw new BizException(ErrorCode.RIGHTS_INSTANCE_NOT_FOUND);
        }
        return toResponse(instance);
    }

    @Override
    public List<RightsInstanceResponse> getByUserId(String userId) {
        List<RightsInstance> instances = rightsInstanceMapper.selectList(
                new LambdaQueryWrapper<RightsInstance>()
                        .eq(RightsInstance::getUserId, userId)
                        .orderByDesc(RightsInstance::getCreateTime));
        return instances.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RightsInstanceResponse activate(String instanceNo) {
        RightsInstance instance = getInstanceOrThrow(instanceNo);

        if (instance.getStatus() != RightsInstanceStatus.PENDING_ACTIVATE.getCode()) {
            throw new BizException(ErrorCode.RIGHTS_STATUS_INVALID,
                    "当前状态不允许激活，当前状态: " + instance.getStatus());
        }

        instance.setStatus(RightsInstanceStatus.ACTIVATED.getCode());
        instance.setActivateTime(LocalDateTime.now());
        rightsInstanceMapper.updateById(instance);

        // Log
        saveLog(instance, 2, 1, null); // 2=激活，1=成功

        log.info("Activated rights instanceNo={}", instanceNo);
        return toResponse(instance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RightsInstanceResponse use(String instanceNo) {
        RightsInstance instance = getInstanceOrThrow(instanceNo);

        if (instance.getStatus() != RightsInstanceStatus.ACTIVATED.getCode()) {
            throw new BizException(ErrorCode.RIGHTS_STATUS_INVALID,
                    "当前状态不允许使用，当前状态: " + instance.getStatus());
        }

        if (instance.getExpireTime() != null && instance.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.RIGHTS_EXPIRED);
        }

        instance.setStatus(RightsInstanceStatus.USED.getCode());
        instance.setUseTime(LocalDateTime.now());
        rightsInstanceMapper.updateById(instance);

        // Log
        saveLog(instance, 3, 1, null); // 3=使用，1=成功

        log.info("Used rights instanceNo={}", instanceNo);
        return toResponse(instance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RightsInstanceResponse revoke(String instanceNo) {
        RightsInstance instance = getInstanceOrThrow(instanceNo);

        if (instance.getStatus() != RightsInstanceStatus.ACTIVATED.getCode()) {
            throw new BizException(ErrorCode.RIGHTS_STATUS_INVALID,
                    "当前状态不允许撤销，当前状态: " + instance.getStatus());
        }

        instance.setStatus(RightsInstanceStatus.REVOKED.getCode());
        rightsInstanceMapper.updateById(instance);

        // Rollback stock
        RightsDefinition definition = rightsDefinitionMapper.selectOne(
                new LambdaQueryWrapper<RightsDefinition>()
                        .eq(RightsDefinition::getRightsCode, instance.getRightsCode()));
        if (definition != null) {
            rightsDefinitionMapper.update(null,
                    new LambdaUpdateWrapper<RightsDefinition>()
                            .eq(RightsDefinition::getId, definition.getId())
                            .setSql("used_stock = used_stock - 1")
                            .gt(RightsDefinition::getUsedStock, 0));
        }

        // Log
        saveLog(instance, 5, 1, null); // 5=撤销，1=成功

        log.info("Revoked rights instanceNo={}", instanceNo);
        return toResponse(instance);
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private RightsInstance getInstanceOrThrow(String instanceNo) {
        RightsInstance instance = rightsInstanceMapper.selectOne(
                new LambdaQueryWrapper<RightsInstance>()
                        .eq(RightsInstance::getInstanceNo, instanceNo));
        if (instance == null) {
            throw new BizException(ErrorCode.RIGHTS_INSTANCE_NOT_FOUND);
        }
        return instance;
    }

    /**
     * Atomic stock deduction: used_stock + 1 <= total_stock
     */
    private boolean deductStock(RightsDefinition definition) {
        int updated = rightsDefinitionMapper.update(null,
                new LambdaUpdateWrapper<RightsDefinition>()
                        .eq(RightsDefinition::getId, definition.getId())
                        .setSql("used_stock = used_stock + 1")
                        .ltSql(RightsDefinition::getUsedStock, "total_stock"));
        return updated > 0;
    }

    private void saveLog(RightsInstance instance, int operationType, int operationResult, String errorMsg) {
        RightsIssueLog issueLog = new RightsIssueLog();
        issueLog.setInstanceNo(instance.getInstanceNo());
        issueLog.setRightsCode(instance.getRightsCode());
        issueLog.setUserId(instance.getUserId());
        issueLog.setSourceType(instance.getSourceType());
        issueLog.setSourceNo(instance.getSourceNo());
        issueLog.setOperationType(operationType);
        issueLog.setOperationResult(operationResult);
        issueLog.setRetryCount(0);
        issueLog.setErrorMsg(errorMsg);
        rightsIssueLogMapper.insert(issueLog);
    }

    private RightsIssueResponse toIssueResponse(RightsInstance instance) {
        RightsIssueResponse resp = new RightsIssueResponse();
        resp.setId(instance.getId());
        resp.setInstanceNo(instance.getInstanceNo());
        resp.setRightsCode(instance.getRightsCode());
        resp.setUserId(instance.getUserId());
        resp.setSourceType(instance.getSourceType());
        resp.setSourceNo(instance.getSourceNo());
        resp.setStatus(instance.getStatus());
        resp.setActivateTime(instance.getActivateTime());
        resp.setExpireTime(instance.getExpireTime());
        resp.setUseTime(instance.getUseTime());
        resp.setSupplierOrderNo(instance.getSupplierOrderNo());
        resp.setCreateTime(instance.getCreateTime());
        return resp;
    }

    private RightsInstanceResponse toResponse(RightsInstance instance) {
        RightsInstanceResponse resp = new RightsInstanceResponse();
        resp.setId(instance.getId());
        resp.setInstanceNo(instance.getInstanceNo());
        resp.setRightsCode(instance.getRightsCode());
        resp.setUserId(instance.getUserId());
        resp.setSourceType(instance.getSourceType());
        resp.setSourceNo(instance.getSourceNo());
        resp.setStatus(instance.getStatus());
        resp.setActivateTime(instance.getActivateTime());
        resp.setExpireTime(instance.getExpireTime());
        resp.setUseTime(instance.getUseTime());
        return resp;
    }
}
