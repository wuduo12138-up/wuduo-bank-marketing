package com.wuduo.bank.activity.infrastructure.util;

import com.wuduo.bank.common.exception.BizException;
import com.wuduo.bank.common.model.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Distributed lock helper for activity service.
 * Lock keys include both activityId and userId for fine-grained concurrency control.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockHelper {

    private static final String LOCK_PREFIX = "activity:lock:";
    private static final long DEFAULT_WAIT_TIME = 3;
    private static final long DEFAULT_LEASE_TIME = 10;

    private final RedissonClient redissonClient;

    /**
     * Execute a task with distributed lock keyed by (activityId, userId).
     */
    public <T> T executeWithLock(Long activityId, Long userId, Supplier<T> supplier) {
        return executeWithLock(activityId, userId, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, supplier);
    }

    /**
     * Execute a task with distributed lock and custom timeouts.
     */
    public <T> T executeWithLock(Long activityId, Long userId, long waitTime, long leaseTime, Supplier<T> supplier) {
        String lockKey = LOCK_PREFIX + activityId + ":" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS)) {
                log.debug("Acquired activity lock: {}", lockKey);
                try {
                    return supplier.get();
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                        log.debug("Released activity lock: {}", lockKey);
                    }
                }
            } else {
                log.warn("Failed to acquire activity lock: {}", lockKey);
                throw new BizException(ErrorCode.TOO_MANY_REQUESTS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while acquiring lock: {}", lockKey, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
