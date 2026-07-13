package com.wuduo.bank.point.boot.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wuduo.bank.point.api.enums.PointRecordStatus;
import com.wuduo.bank.point.domain.entity.PointAccount;
import com.wuduo.bank.point.domain.entity.PointRecord;
import com.wuduo.bank.point.infrastructure.mapper.PointAccountMapper;
import com.wuduo.bank.point.infrastructure.mapper.PointRecordMapper;
import com.wuduo.bank.point.infrastructure.util.DistributedLockHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Point expire scheduled task
 * Runs every hour to scan and process expired point records
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointExpireTask {

    private final PointRecordMapper pointRecordMapper;
    private final PointAccountMapper pointAccountMapper;
    private final DistributedLockHelper lockHelper;

    private static final int BATCH_SIZE = 200;

    /**
     * Scan expired point records every hour at minute 5
     */
    @Scheduled(cron = "0 5 * * * ?")
    public void processExpiredPoints() {
        log.info("Starting point expire task...");

        long totalExpired = 0;
        int offset = 0;

        while (true) {
            // Query expired records in batches
            List<PointRecord> expiredRecords = pointRecordMapper.selectList(
                    new LambdaQueryWrapper<PointRecord>()
                            .eq(PointRecord::getStatus, PointRecordStatus.VALID.getCode())
                            .lt(PointRecord::getExpireTime, LocalDateTime.now())
                            .last("LIMIT " + offset + ", " + BATCH_SIZE));

            if (expiredRecords.isEmpty()) {
                break;
            }

            // Group by userId to minimize lock contention
            Map<Long, List<PointRecord>> userGrouped = expiredRecords.stream()
                    .collect(Collectors.groupingBy(PointRecord::getUserId));

            for (Map.Entry<Long, List<PointRecord>> entry : userGrouped.entrySet()) {
                Long userId = entry.getKey();
                List<PointRecord> records = entry.getValue();
                try {
                    lockHelper.executeWithLock(userId, () -> {
                        processUserExpiredRecords(userId, records);
                        return null;
                    });
                    totalExpired += records.size();
                } catch (Exception e) {
                    log.error("Failed to process expired points for userId={}", userId, e);
                }
            }

            offset += BATCH_SIZE;
        }

        log.info("Point expire task completed. Total expired records: {}", totalExpired);
    }

    private void processUserExpiredRecords(Long userId, List<PointRecord> records) {
        long totalExpiredAmount = 0L;

        for (PointRecord record : records) {
            long remainingAmount = record.getPointAmount() - record.getUsedAmount();
            if (remainingAmount <= 0) {
                // Already fully used via FIFO, just mark as expired
                record.setStatus(PointRecordStatus.EXPIRED.getCode());
                pointRecordMapper.updateById(record);
                continue;
            }

            totalExpiredAmount += remainingAmount;

            // Mark record as expired
            record.setStatus(PointRecordStatus.EXPIRED.getCode());
            record.setUsedAmount(record.getPointAmount()); // Mark all as used
            pointRecordMapper.updateById(record);
        }

        if (totalExpiredAmount <= 0) {
            return;
        }

        // Update account: decrement available, increment totalExpired
        PointAccount account = pointAccountMapper.selectOne(
                new LambdaQueryWrapper<PointAccount>()
                        .eq(PointAccount::getUserId, userId));
        if (account == null) {
            log.warn("Point account not found for userId={}, skip expires", userId);
            return;
        }

        // Optimistic lock retry for account update
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            Integer currentVersion = account.getVersion();
            account.setAvailable(account.getAvailable() - totalExpiredAmount);
            account.setTotalExpired(account.getTotalExpired() + totalExpiredAmount);
            account.setVersion(currentVersion);

            int updated = pointAccountMapper.updateById(account);
            if (updated > 0) {
                log.info("Expired {} points from userId={}, new available={}",
                        totalExpiredAmount, userId, account.getAvailable());
                return;
            }

            // Version conflict, re-query
            log.warn("Expire task version conflict for userId={}, retry {}/{}", userId, i + 1, maxRetries);
            account = pointAccountMapper.selectOne(
                    new LambdaQueryWrapper<PointAccount>()
                            .eq(PointAccount::getUserId, userId));
            if (account == null) {
                return;
            }
            if (i < maxRetries - 1) {
                try {
                    Thread.sleep((long) (Math.random() * 50 * (i + 1)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("Failed to update account for userId={} after {} retries", userId, maxRetries);
    }
}
