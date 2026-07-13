package com.wuduo.bank.point.infrastructure.util;

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
 * Distributed lock helper using Redisson RLock
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockHelper {

    private static final String LOCK_PREFIX = "point:lock:";
    private static final long DEFAULT_WAIT_TIME = 3;
    private static final long DEFAULT_LEASE_TIME = 10;

    private final RedissonClient redissonClient;

    /**
     * Execute a task with distributed lock by userId
     *
     * @param userId   the user ID used for locking
     * @param supplier the task to execute
     * @param <T>      return type
     * @return task result
     */
    public <T> T executeWithLock(Long userId, Supplier<T> supplier) {
        return executeWithLock(userId, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, supplier);
    }

    /**
     * Execute a task with distributed lock by userId and custom timeouts
     *
     * @param userId    the user ID used for locking
     * @param waitTime  max wait time for lock acquisition (seconds)
     * @param leaseTime lock lease time (seconds)
     * @param supplier  the task to execute
     * @param <T>       return type
     * @return task result
     */
    public <T> T executeWithLock(Long userId, long waitTime, long leaseTime, Supplier<T> supplier) {
        String lockKey = LOCK_PREFIX + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS)) {
                log.debug("Acquired lock for userId: {}", userId);
                try {
                    return supplier.get();
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                        log.debug("Released lock for userId: {}", userId);
                    }
                }
            } else {
                log.warn("Failed to acquire lock for userId: {}", userId);
                throw new BizException(ErrorCode.TOO_MANY_REQUESTS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while acquiring lock for userId: {}", userId, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
