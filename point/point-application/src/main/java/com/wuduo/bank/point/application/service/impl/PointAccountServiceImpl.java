package com.wuduo.bank.point.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wuduo.bank.common.exception.BizException;
import com.wuduo.bank.common.model.ErrorCode;
import com.wuduo.bank.point.api.dto.*;
import com.wuduo.bank.point.api.enums.PointFreezeStatus;
import com.wuduo.bank.point.application.service.PointAccountService;
import com.wuduo.bank.point.domain.entity.PointAccount;
import com.wuduo.bank.point.domain.entity.PointFreeze;
import com.wuduo.bank.point.domain.entity.PointRecord;
import com.wuduo.bank.point.infrastructure.mapper.PointAccountMapper;
import com.wuduo.bank.point.infrastructure.mapper.PointFreezeMapper;
import com.wuduo.bank.point.infrastructure.mapper.PointRecordMapper;
import com.wuduo.bank.point.infrastructure.util.DistributedLockHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Point account application service implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointAccountServiceImpl implements PointAccountService {

    private static final int MAX_RETRIES = 3;

    private final PointAccountMapper pointAccountMapper;
    private final PointRecordMapper pointRecordMapper;
    private final PointFreezeMapper pointFreezeMapper;
    private final DistributedLockHelper lockHelper;

    // ===================================================
    // Issue
    // ===================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointAccountResponse issue(PointIssueRequest request) {
        validateAmountPositive(request.getPointAmount(), "pointAmount");

        return lockHelper.executeWithLock(request.getUserId(), () -> {
            PointAccount account = getOrCreateAccount(request.getUserId());

            // Idempotent check
            if (request.getBizNo() != null && !request.getBizNo().isEmpty()) {
                Long count = pointRecordMapper.selectCount(
                        new LambdaQueryWrapper<PointRecord>()
                                .eq(PointRecord::getUserId, request.getUserId())
                                .eq(PointRecord::getBizNo, request.getBizNo()));
                if (count > 0) {
                    throw new BizException(ErrorCode.POINT_DUPLICATE_ISSUE);
                }
            }

            // Create point record
            PointRecord record = new PointRecord();
            record.setRecordNo(generateRecordNo());
            record.setUserId(request.getUserId());
            record.setPointAmount(request.getPointAmount());
            record.setType(request.getType());
            record.setBizSource(request.getBizSource());
            record.setBizNo(request.getBizNo());
            record.setExpireTime(request.getExpireTime());
            record.setStatus(com.wuduo.bank.point.api.enums.PointRecordStatus.VALID.getCode());
            record.setUsedAmount(0L);
            pointRecordMapper.insert(record);

            // Update account with optimistic lock retry
            account = updateAccountWithRetry(account, a -> {
                a.setTotalEarned(a.getTotalEarned() + request.getPointAmount());
                a.setAvailable(a.getAvailable() + request.getPointAmount());
            });

            return toResponse(account);
        });
    }

    // ===================================================
    // Freeze
    // ===================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointFreezeResponse freeze(PointFreezeRequest request) {
        validateAmountPositive(request.getFreezeAmount(), "freezeAmount");

        return lockHelper.executeWithLock(request.getUserId(), () -> {
            // Idempotent check
            PointFreeze existingFreeze = pointFreezeMapper.selectOne(
                    new LambdaQueryWrapper<PointFreeze>()
                            .eq(PointFreeze::getBizNo, request.getBizNo()));
            if (existingFreeze != null) {
                return toFreezeResponse(existingFreeze);
            }

            PointAccount account = getAccount(request.getUserId());
            if (account.getAvailable() < request.getFreezeAmount()) {
                throw new BizException(ErrorCode.POINT_BALANCE_INSUFFICIENT);
            }

            // Create freeze record
            PointFreeze freeze = new PointFreeze();
            freeze.setFreezeNo(generateFreezeNo());
            freeze.setUserId(request.getUserId());
            freeze.setFreezeAmount(request.getFreezeAmount());
            freeze.setBizNo(request.getBizNo());
            freeze.setStatus(PointFreezeStatus.FROZEN.getCode());
            pointFreezeMapper.insert(freeze);

            // Update account
            account = updateAccountWithRetry(account, a -> {
                a.setAvailable(a.getAvailable() - request.getFreezeAmount());
                a.setFrozen(a.getFrozen() + request.getFreezeAmount());
            });

            // Refresh freeze from DB to get auto-filled fields
            freeze = pointFreezeMapper.selectById(freeze.getId());
            return toFreezeResponse(freeze);
        });
    }

    // ===================================================
    // Unfreeze
    // ===================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointFreezeResponse unfreeze(PointFreezeRequest request) {
        return lockHelper.executeWithLock(request.getUserId(), () -> {
            PointFreeze freeze = pointFreezeMapper.selectOne(
                    new LambdaQueryWrapper<PointFreeze>()
                            .eq(PointFreeze::getBizNo, request.getBizNo()));
            if (freeze == null) {
                throw new BizException(ErrorCode.POINT_FREEZE_NOT_FOUND);
            }
            if (PointFreezeStatus.FROZEN.getCode() != (int) freeze.getStatus()) {
                throw new BizException(ErrorCode.POINT_FREEZE_STATUS_INVALID);
            }

            PointAccount account = getAccount(request.getUserId());

            // Update freeze status
            freeze.setStatus(PointFreezeStatus.UNFROZEN.getCode());
            pointFreezeMapper.updateById(freeze);

            // Update account
            account = updateAccountWithRetry(account, a -> {
                a.setAvailable(a.getAvailable() + freeze.getFreezeAmount());
                a.setFrozen(a.getFrozen() - freeze.getFreezeAmount());
            });

            return toFreezeResponse(freeze);
        });
    }

    // ===================================================
    // Deduct
    // ===================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointAccountResponse deduct(PointDeductRequest request) {
        validateAmountPositive(request.getPointAmount(), "pointAmount");

        return lockHelper.executeWithLock(request.getUserId(), () -> {
            // Idempotent check
            if (request.getBizNo() != null && !request.getBizNo().isEmpty()) {
                Long count = pointRecordMapper.selectCount(
                        new LambdaQueryWrapper<PointRecord>()
                                .eq(PointRecord::getUserId, request.getUserId())
                                .eq(PointRecord::getBizNo, request.getBizNo()));
                if (count > 0) {
                    throw new BizException(ErrorCode.POINT_DUPLICATE_ISSUE);
                }
            }

            PointAccount account = getAccount(request.getUserId());

            if (request.getFreezeNo() != null && !request.getFreezeNo().isEmpty()) {
                // Deduct from frozen
                return deductFromFrozen(request, account);
            } else {
                // FIFO direct deduct
                return deductFifo(request, account);
            }
        });
    }

    private PointAccountResponse deductFromFrozen(PointDeductRequest request, PointAccount account) {
        PointFreeze freeze = pointFreezeMapper.selectOne(
                new LambdaQueryWrapper<PointFreeze>()
                        .eq(PointFreeze::getFreezeNo, request.getFreezeNo()));
        if (freeze == null) {
            throw new BizException(ErrorCode.POINT_FREEZE_NOT_FOUND);
        }
        if (PointFreezeStatus.FROZEN.getCode() != (int) freeze.getStatus()) {
            throw new BizException(ErrorCode.POINT_FREEZE_STATUS_INVALID);
        }
        if (freeze.getFreezeAmount() < request.getPointAmount()) {
            throw new BizException(ErrorCode.POINT_FREEZE_AMOUNT_INSUFFICIENT);
        }

        // Update freeze status
        freeze.setStatus(PointFreezeStatus.DEDUCTED.getCode());
        pointFreezeMapper.updateById(freeze);

        // Mark usedAmount on earn records (FIFO) so record-level sums stay consistent
        // with account-level totals — same as deductFifo, for reconciliation correctness
        consumeEarnRecords(request.getUserId(), request.getPointAmount());

        // Update account
        account = updateAccountWithRetry(account, a -> {
            a.setFrozen(a.getFrozen() - request.getPointAmount());
            a.setTotalUsed(a.getTotalUsed() + request.getPointAmount());
        });

        // Create deduct record
        PointRecord record = new PointRecord();
        record.setRecordNo(generateRecordNo());
        record.setUserId(request.getUserId());
        record.setPointAmount(-request.getPointAmount());
        record.setType(request.getType());
        record.setBizSource(request.getBizSource());
        record.setBizNo(request.getBizNo());
        record.setStatus(com.wuduo.bank.point.api.enums.PointRecordStatus.USED.getCode());
        record.setUsedAmount(0L);
        record.setRemark("Deduct from freeze: " + request.getFreezeNo());
        pointRecordMapper.insert(record);

        return toResponse(account);
    }

    private PointAccountResponse deductFifo(PointDeductRequest request, PointAccount account) {
        if (account.getAvailable() < request.getPointAmount()) {
            throw new BizException(ErrorCode.POINT_BALANCE_INSUFFICIENT);
        }

        // Mark usedAmount on earn records (FIFO)
        consumeEarnRecords(request.getUserId(), request.getPointAmount());

        // Update account
        account = updateAccountWithRetry(account, a -> {
            a.setAvailable(a.getAvailable() - request.getPointAmount());
            a.setTotalUsed(a.getTotalUsed() + request.getPointAmount());
        });

        // Create deduct record
        PointRecord deductRecord = new PointRecord();
        deductRecord.setRecordNo(generateRecordNo());
        deductRecord.setUserId(request.getUserId());
        deductRecord.setPointAmount(-request.getPointAmount());
        deductRecord.setType(request.getType());
        deductRecord.setBizSource(request.getBizSource());
        deductRecord.setBizNo(request.getBizNo());
        deductRecord.setStatus(com.wuduo.bank.point.api.enums.PointRecordStatus.USED.getCode());
        deductRecord.setUsedAmount(0L);
        deductRecord.setRemark("FIFO deduction");
        pointRecordMapper.insert(deductRecord);

        return toResponse(account);
    }

    /**
     * Consume points from VALID earn records in FIFO order by incrementing usedAmount.
     * Shared by both FIFO direct deduction and frozen deduction paths so that
     * record-level sums always reconcile with account-level totals.
     */
    private void consumeEarnRecords(Long userId, long amountToConsume) {
        long remaining = amountToConsume;

        List<PointRecord> validRecords = pointRecordMapper.selectList(
                new LambdaQueryWrapper<PointRecord>()
                        .eq(PointRecord::getUserId, userId)
                        .eq(PointRecord::getStatus, com.wuduo.bank.point.api.enums.PointRecordStatus.VALID.getCode())
                        .orderByAsc(PointRecord::getCreatedAt));

        for (PointRecord record : validRecords) {
            if (remaining <= 0) {
                break;
            }

            long availableInRecord = record.getPointAmount() - record.getUsedAmount();
            if (availableInRecord <= 0) {
                continue;
            }

            long deductFromRecord = Math.min(availableInRecord, remaining);
            record.setUsedAmount(record.getUsedAmount() + deductFromRecord);

            if (record.getUsedAmount() >= record.getPointAmount()) {
                record.setStatus(com.wuduo.bank.point.api.enums.PointRecordStatus.USED.getCode());
            }
            pointRecordMapper.updateById(record);

            remaining -= deductFromRecord;
        }

        if (remaining > 0) {
            throw new BizException(ErrorCode.POINT_BALANCE_INSUFFICIENT);
        }
    }

    // ===================================================
    // Refund
    // ===================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointAccountResponse refund(PointRefundRequest request) {
        return lockHelper.executeWithLock(request.getUserId(), () -> {
            PointRecord originalRecord = pointRecordMapper.selectOne(
                    new LambdaQueryWrapper<PointRecord>()
                            .eq(PointRecord::getRecordNo, request.getOriginalRecordNo()));
            if (originalRecord == null) {
                throw new BizException(ErrorCode.POINT_RECORD_NOT_FOUND);
            }
            if (com.wuduo.bank.point.api.enums.PointRecordStatus.USED.getCode() != (int) originalRecord.getStatus()) {
                throw new BizException(ErrorCode.POINT_RECORD_STATUS_INVALID);
            }
            // Only refund deduction records (negative amounts)
            if (originalRecord.getPointAmount() >= 0) {
                throw new BizException(ErrorCode.POINT_RECORD_STATUS_INVALID);
            }

            PointAccount account = getAccount(request.getUserId());
            long refundAmount = -originalRecord.getPointAmount();

            // Mark original record as REVOKED
            originalRecord.setStatus(com.wuduo.bank.point.api.enums.PointRecordStatus.REVOKED.getCode());
            pointRecordMapper.updateById(originalRecord);

            // Create refund earn record
            PointRecord refundRecord = new PointRecord();
            refundRecord.setRecordNo(generateRecordNo());
            refundRecord.setUserId(request.getUserId());
            refundRecord.setPointAmount(refundAmount);
            refundRecord.setType(com.wuduo.bank.point.api.enums.PointRecordType.REFUND_EARN.getCode());
            refundRecord.setBizSource("POINT_REFUND");
            refundRecord.setBizNo(null);
            refundRecord.setStatus(com.wuduo.bank.point.api.enums.PointRecordStatus.VALID.getCode());
            refundRecord.setUsedAmount(0L);
            refundRecord.setRemark(request.getRemark() != null ? request.getRemark() : "Refund for record: " + request.getOriginalRecordNo());
            pointRecordMapper.insert(refundRecord);

            // Update account
            account = updateAccountWithRetry(account, a -> {
                a.setAvailable(a.getAvailable() + refundAmount);
                a.setTotalUsed(a.getTotalUsed() - refundAmount);
            });

            return toResponse(account);
        });
    }

    // ===================================================
    // Query
    // ===================================================

    @Override
    public PointAccountResponse getByUserId(Long userId) {
        PointAccount account = pointAccountMapper.selectOne(
                new LambdaQueryWrapper<PointAccount>()
                        .eq(PointAccount::getUserId, userId));
        if (account == null) {
            // Return empty account instead of throwing
            PointAccountResponse response = new PointAccountResponse();
            response.setUserId(userId);
            response.setTotalEarned(0L);
            response.setTotalUsed(0L);
            response.setTotalExpired(0L);
            response.setFrozen(0L);
            response.setAvailable(0L);
            return response;
        }
        return toResponse(account);
    }

    // ===================================================
    // Optimistic lock retry
    // ===================================================

    /**
     * Update account with optimistic lock retry.
     * Uses explicit version condition in WHERE clause to ensure atomicity,
     * rather than relying on MyBatis-Plus @Version interceptor alone.
     *
     * @param account    current account snapshot (must have id and version populated)
     * @param applyDelta consumer that mutates the account fields in-place
     * @return refreshed account after successful update
     */
    private PointAccount updateAccountWithRetry(PointAccount account, Consumer<PointAccount> applyDelta) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            Integer currentVersion = account.getVersion();
            applyDelta.accept(account);

            // Build explicit optimistic-lock UPDATE:
            // SET fields = new values, version = version + 1
            // WHERE id = ? AND version = currentVersion
            LambdaUpdateWrapper<PointAccount> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(PointAccount::getId, account.getId())
                    .eq(PointAccount::getVersion, currentVersion)
                    .set(PointAccount::getVersion, currentVersion + 1)
                    .set(PointAccount::getTotalEarned, account.getTotalEarned())
                    .set(PointAccount::getTotalUsed, account.getTotalUsed())
                    .set(PointAccount::getTotalExpired, account.getTotalExpired())
                    .set(PointAccount::getFrozen, account.getFrozen())
                    .set(PointAccount::getAvailable, account.getAvailable());

            int updated = pointAccountMapper.update(null, wrapper);
            if (updated > 0) {
                // Update succeeded — bump in-memory version and return
                account.setVersion(currentVersion + 1);
                return account;
            }

            // Version conflict — re-query and retry
            log.warn("Optimistic lock conflict for userId={}, accountId={}, retry {}/{}",
                    account.getUserId(), account.getId(), i + 1, MAX_RETRIES);

            PointAccount latest = pointAccountMapper.selectById(account.getId());
            if (latest == null) {
                throw new BizException(ErrorCode.INTERNAL_ERROR);
            }
            account = latest;

            if (i < MAX_RETRIES - 1) {
                try {
                    long sleepMs = (long) (Math.random() * 50 * (i + 1));
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BizException(ErrorCode.INTERNAL_ERROR);
                }
            }
        }
        throw new BizException(ErrorCode.POINT_VERSION_CONFLICT);
    }

    // ===================================================
    // Helper methods
    // ===================================================

    private PointAccount getOrCreateAccount(Long userId) {
        PointAccount account = pointAccountMapper.selectOne(
                new LambdaQueryWrapper<PointAccount>()
                        .eq(PointAccount::getUserId, userId));
        if (account == null) {
            account = new PointAccount();
            account.setUserId(userId);
            account.setTotalEarned(0L);
            account.setTotalUsed(0L);
            account.setTotalExpired(0L);
            account.setFrozen(0L);
            account.setAvailable(0L);
            account.setVersion(0);
            pointAccountMapper.insert(account);
        }
        return account;
    }

    private PointAccount getAccount(Long userId) {
        PointAccount account = pointAccountMapper.selectOne(
                new LambdaQueryWrapper<PointAccount>()
                        .eq(PointAccount::getUserId, userId));
        if (account == null) {
            throw new BizException(ErrorCode.POINT_ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    private void validateAmountPositive(Long amount, String fieldName) {
        if (amount == null || amount <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), fieldName + " must be positive");
        }
    }

    private String generateRecordNo() {
        return "PNT" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateFreezeNo() {
        return "FRZ" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // ===================================================
    // DTO conversion
    // ===================================================

    private PointAccountResponse toResponse(PointAccount account) {
        PointAccountResponse response = new PointAccountResponse();
        response.setUserId(account.getUserId());
        response.setTotalEarned(account.getTotalEarned());
        response.setTotalUsed(account.getTotalUsed());
        response.setTotalExpired(account.getTotalExpired());
        response.setFrozen(account.getFrozen());
        response.setAvailable(account.getAvailable());
        return response;
    }

    private PointFreezeResponse toFreezeResponse(PointFreeze freeze) {
        PointFreezeResponse response = new PointFreezeResponse();
        response.setId(freeze.getId());
        response.setFreezeNo(freeze.getFreezeNo());
        response.setUserId(freeze.getUserId());
        response.setFreezeAmount(freeze.getFreezeAmount());
        response.setStatus(freeze.getStatus());
        response.setBizNo(freeze.getBizNo());
        response.setRemark(freeze.getRemark());
        response.setCreatedAt(freeze.getCreatedAt());
        return response;
    }
}
