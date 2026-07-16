package com.wuduo.bank.rights.infrastructure.util;

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
 * Distributed lock helper using Redisson RLock for rights module.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockHelper {

    private static final String LOCK_PREFIX = "rights:lock:";
    private static final long DEFAULT_WAIT_TIME = 3;
    private static final long DEFAULT_LEASE_TIME = 10;

    private final RedissonClient redissonClient;

    /**
     * Execute a task with distributed lock by rightsCode and userId.
     */
    public <T> T executeWithLock(String rightsCode, String userId, Supplier<T> supplier) {
        return executeWithLock(rightsCode, userId, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, supplier);
    }

    /**
     * Execute a task with distributed lock and custom timeouts.
     */
    public <T> T executeWithLock(String rightsCode, String userId, long waitTime, long leaseTime, Supplier<T> supplier) {
        String lockKey = LOCK_PREFIX + rightsCode + ":" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS)) {
                log.debug("Acquired lock for rightsCode={} userId={}", rightsCode, userId);
                try {
                    return supplier.get();
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                        log.debug("Released lock for rightsCode={} userId={}", rightsCode, userId);
                    }
                }
            } else {
                log.warn("Failed to acquire lock for rightsCode={} userId={}", rightsCode, userId);
                throw new BizException(ErrorCode.TOO_MANY_REQUESTS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while acquiring lock for rightsCode={} userId={}", rightsCode, userId, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
