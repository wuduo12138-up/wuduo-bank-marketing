package com.wuduo.bank.rights.boot.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuduo.bank.rights.api.enums.RightsInstanceStatus;
import com.wuduo.bank.rights.domain.entity.RightsInstance;
import com.wuduo.bank.rights.domain.entity.RightsIssueLog;
import com.wuduo.bank.rights.infrastructure.mapper.RightsInstanceMapper;
import com.wuduo.bank.rights.infrastructure.mapper.RightsIssueLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Rights expire scheduled task.
 * Runs every hour to scan and mark expired rights instances.
 *
 * <p>Pattern follows {@code PointExpireTask} but simpler:
 * only status updates are needed — no account balance changes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RightsExpireTask {

    private final RightsInstanceMapper rightsInstanceMapper;
    private final RightsIssueLogMapper rightsIssueLogMapper;

    private static final int BATCH_SIZE = 200;

    /**
     * Scan expired rights instances every hour at minute 10.
     * Runs at :10 to avoid overlapping with point expire task at :05.
     */
    @Scheduled(cron = "0 10 * * * ?")
    public void processExpiredRights() {
        log.info("Starting rights expire task...");

        long totalExpired = 0;
        int offset = 0;

        while (true) {
            // Query expired but still activated instances in batches
            List<RightsInstance> expiredInstances = rightsInstanceMapper.selectList(
                    new LambdaQueryWrapper<RightsInstance>()
                            .eq(RightsInstance::getStatus, RightsInstanceStatus.ACTIVATED.getCode())
                            .lt(RightsInstance::getExpireTime, LocalDateTime.now())
                            .last("LIMIT " + offset + ", " + BATCH_SIZE));

            if (expiredInstances.isEmpty()) {
                break;
            }

            for (RightsInstance instance : expiredInstances) {
                try {
                    instance.setStatus(RightsInstanceStatus.EXPIRED.getCode());
                    rightsInstanceMapper.updateById(instance);

                    // Write expire log
                    RightsIssueLog issueLog = new RightsIssueLog();
                    issueLog.setInstanceNo(instance.getInstanceNo());
                    issueLog.setRightsCode(instance.getRightsCode());
                    issueLog.setUserId(instance.getUserId());
                    issueLog.setSourceType(instance.getSourceType());
                    issueLog.setSourceNo(instance.getSourceNo());
                    issueLog.setOperationType(4); // 4=过期
                    issueLog.setOperationResult(1); // 1=成功
                    issueLog.setRetryCount(0);
                    rightsIssueLogMapper.insert(issueLog);

                    totalExpired++;
                } catch (Exception e) {
                    log.error("Failed to expire rights instance instanceNo={}", instance.getInstanceNo(), e);
                }
            }

            offset += BATCH_SIZE;
        }

        log.info("Rights expire task completed. Total expired instances: {}", totalExpired);
    }
}
