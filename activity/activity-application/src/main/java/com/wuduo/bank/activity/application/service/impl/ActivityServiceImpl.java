package com.wuduo.bank.activity.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuduo.bank.activity.api.dto.ActivityCreateRequest;
import com.wuduo.bank.activity.api.dto.ActivityResponse;
import com.wuduo.bank.activity.api.dto.ActivityUpdateRequest;
import com.wuduo.bank.activity.api.enums.ActivityStatus;
import com.wuduo.bank.activity.application.service.ActivityService;
import com.wuduo.bank.activity.domain.entity.Activity;
import com.wuduo.bank.activity.domain.entity.ActivityParticipation;
import com.wuduo.bank.activity.domain.entity.ActivityVersion;
import com.wuduo.bank.activity.infrastructure.mapper.ActivityMapper;
import com.wuduo.bank.activity.infrastructure.mapper.ActivityParticipationMapper;
import com.wuduo.bank.activity.infrastructure.mapper.ActivityVersionMapper;
import com.wuduo.bank.common.exception.BizException;
import com.wuduo.bank.common.model.ErrorCode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Activity application service implementation — draft + version dual-track audit flow.
 * <p>
 * Each edit creates a new {@link ActivityVersion} row with a full content snapshot.
 * The online version is determined by {@link Activity#getOnlineVersionId()}.
 * Editing a live activity never mutates the live data until the new version is approved.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private static final DateTimeFormatter CODE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ActivityMapper activityMapper;
    private final ActivityVersionMapper versionMapper;
    private final ActivityParticipationMapper participationMapper;
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------
    // 1. Create
    // -------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityResponse create(ActivityCreateRequest request) {
        // Validate
        if (request.getBudgetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "预算金额必须大于0");
        }
        if (request.getStartTime() != null && request.getEndTime() != null
                && !request.getStartTime().isBefore(request.getEndTime())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "开始时间必须早于结束时间");
        }

        // Generate activity code
        String activityCode = "ACT" + LocalDateTime.now().format(CODE_DATE_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));

        // Determine initial status
        boolean submit = Boolean.TRUE.equals(request.getSubmit());
        int initialStatus = submit ? ActivityStatus.PENDING_AUDIT.getCode() : ActivityStatus.DRAFT.getCode();
        int initialAuditStatus = submit ? VersionAuditStatus.PENDING_AUDIT : VersionAuditStatus.DRAFT;

        // Save activity
        Activity activity = new Activity();
        activity.setActivityCode(activityCode);
        activity.setTitle(request.getTitle());
        activity.setType(request.getType());
        activity.setStatus(initialStatus);
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setBudgetAmount(request.getBudgetAmount());
        activity.setBudgetUsed(BigDecimal.ZERO);
        activity.setRuleConfig(request.getRuleConfig());
        activityMapper.insert(activity);

        // Create initial version V1
        VersionContent content = VersionContent.from(request);
        ActivityVersion version = new ActivityVersion();
        version.setActivityId(activity.getId());
        version.setVersion(1);
        version.setContent(toJson(content));
        version.setAuditStatus(initialAuditStatus);
        version.setIsOnline(0);
        versionMapper.insert(version);

        log.info("Created activity id={} code={} status={} version V1 auditStatus={}",
                activity.getId(), activityCode, initialStatus, initialAuditStatus);
        return toResponse(activity, version);
    }

    // -------------------------------------------------------
    // 2. Update
    // -------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityResponse update(ActivityUpdateRequest request) {
        Activity activity = activityMapper.selectById(request.getId());
        if (activity == null) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        // Find the latest version
        ActivityVersion latestVersion = versionMapper.selectOne(
                new LambdaQueryWrapper<ActivityVersion>()
                        .eq(ActivityVersion::getActivityId, activity.getId())
                        .orderByDesc(ActivityVersion::getVersion)
                        .last("LIMIT 1"));
        if (latestVersion == null) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_FOUND, "活动版本记录缺失");
        }

        int status = activity.getStatus();
        boolean submit = Boolean.TRUE.equals(request.getSubmit());

        if (status == ActivityStatus.DRAFT.getCode() || status == ActivityStatus.REJECTED.getCode()) {
            // === Path A: Initial editing — modify V1 content directly ===
            updateVersionContent(latestVersion, request);
            if (submit) {
                latestVersion.setAuditStatus(VersionAuditStatus.PENDING_AUDIT);
                activity.setStatus(ActivityStatus.PENDING_AUDIT.getCode());
            } else {
                latestVersion.setAuditStatus(VersionAuditStatus.DRAFT);
                activity.setStatus(ActivityStatus.DRAFT.getCode());
            }
            versionMapper.updateById(latestVersion);
            activityMapper.updateById(activity);

        } else if (status == ActivityStatus.PUBLISHED.getCode()
                || status == ActivityStatus.ONGOING.getCode()) {
            // === Path B: Live editing — deep copy online version → new version row ===
            ActivityVersion onlineVersion = findOnlineVersion(activity);
            if (onlineVersion == null) {
                throw new BizException(ErrorCode.ACTIVITY_NOT_FOUND, "找不到线上版本");
            }

            // Deep copy online version content → merge updates
            VersionContent content = parseContent(onlineVersion.getContent());
            content.mergeFrom(request);

            ActivityVersion newVersion = new ActivityVersion();
            newVersion.setActivityId(activity.getId());
            newVersion.setVersion(latestVersion.getVersion() + 1);
            newVersion.setContent(toJson(content));
            newVersion.setAuditStatus(submit ? VersionAuditStatus.PENDING_AUDIT
                    : VersionAuditStatus.DRAFT);
            newVersion.setIsOnline(0);
            versionMapper.insert(newVersion);

            // Activity status stays unchanged — online activity unaffected
            log.info("Created new version V{} for activity id={} (online V{} still serving)",
                    newVersion.getVersion(), activity.getId(), onlineVersion.getVersion());

            return toResponse(activity, newVersion);
        } else {
            throw new BizException(ErrorCode.ACTIVITY_STATUS_INVALID,
                    "当前状态(" + status + ")不可编辑");
        }

        return toResponse(activity, latestVersion);
    }

    // -------------------------------------------------------
    // 3. Get by ID
    // -------------------------------------------------------

    @Override
    public ActivityResponse getById(Long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        return toResponse(activity, findLatestVersion(id));
    }

    // -------------------------------------------------------
    // 4. Page
    // -------------------------------------------------------

    @Override
    public List<ActivityResponse> page(Integer pageNum, Integer pageSize, Integer status, Integer type) {
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Activity::getStatus, status);
        }
        if (type != null) {
            wrapper.eq(Activity::getType, type);
        }
        wrapper.orderByDesc(Activity::getCreatedAt);

        IPage<Activity> page = activityMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);

        return page.getRecords().stream()
                .map(act -> toResponse(act, findLatestVersion(act.getId())))
                .toList();
    }

    // -------------------------------------------------------
    // 5. Audit
    // -------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityResponse audit(Long id, Boolean approved) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        // Find the version pending audit
        ActivityVersion pendingVersion = versionMapper.selectOne(
                new LambdaQueryWrapper<ActivityVersion>()
                        .eq(ActivityVersion::getActivityId, id)
                        .eq(ActivityVersion::getAuditStatus, VersionAuditStatus.PENDING_AUDIT)
                        .orderByDesc(ActivityVersion::getVersion)
                        .last("LIMIT 1"));

        if (pendingVersion == null) {
            throw new BizException(ErrorCode.ACTIVITY_STATUS_INVALID, "没有待审批的版本");
        }

        if (Boolean.TRUE.equals(approved)) {
            // --- Approve ---
            int status = activity.getStatus();
            boolean isInitialAudit = (status == ActivityStatus.PENDING_AUDIT.getCode());

            if (isInitialAudit) {
                // Initial creation audit: V1 goes online
                pendingVersion.setAuditStatus(VersionAuditStatus.APPROVED);
                pendingVersion.setIsOnline(1);
                activity.setOnlineVersionId(pendingVersion.getId());
                activity.setStatus(ActivityStatus.PUBLISHED.getCode());
                applyContentToActivity(activity, parseContent(pendingVersion.getContent()));
                versionMapper.updateById(pendingVersion);
                activityMapper.updateById(activity);

                log.info("Activity {} initial audit approved, V1 now online", id);
            } else {
                // Edit audit: swap online version
                ActivityVersion oldOnline = findOnlineVersion(activity);
                if (oldOnline != null) {
                    oldOnline.setIsOnline(0);
                    versionMapper.updateById(oldOnline);
                }

                pendingVersion.setAuditStatus(VersionAuditStatus.APPROVED);
                pendingVersion.setIsOnline(1);
                activity.setOnlineVersionId(pendingVersion.getId());
                applyContentToActivity(activity, parseContent(pendingVersion.getContent()));
                versionMapper.updateById(pendingVersion);
                activityMapper.updateById(activity);

                log.info("Activity {} edit audit approved, V{} now online (was V{})",
                        id, pendingVersion.getVersion(),
                        oldOnline != null ? oldOnline.getVersion() : "?");
            }
        } else {
            // --- Reject ---
            pendingVersion.setAuditStatus(VersionAuditStatus.REJECTED);
            versionMapper.updateById(pendingVersion);

            // Initial creation rejection → activity status back to REJECTED
            if (activity.getStatus() == ActivityStatus.PENDING_AUDIT.getCode()) {
                activity.setStatus(ActivityStatus.REJECTED.getCode());
                activityMapper.updateById(activity);
            }
            // Edit rejection → activity status unchanged (still ONGOING)

            log.info("Activity {} version V{} audit rejected", id, pendingVersion.getVersion());
        }

        return toResponse(activity, findLatestVersion(id));
    }

    // -------------------------------------------------------
    // 6. Online
    // -------------------------------------------------------

    @Override
    public ActivityResponse online(Long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        ActivityVersion onlineVersion = findOnlineVersion(activity);
        if (onlineVersion == null) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_ONGOING, "该活动暂无线上版本");
        }
        return toResponse(activity, onlineVersion);
    }

    // -------------------------------------------------------
    // 6b. Pending audit list
    // -------------------------------------------------------

    @Override
    public List<ActivityResponse> pendingAudit(Integer pageNum, Integer pageSize) {
        // Find all activity_ids that have a version pending audit
        List<ActivityVersion> pendingVersions = versionMapper.selectList(
                new LambdaQueryWrapper<ActivityVersion>()
                        .select(ActivityVersion::getActivityId)
                        .eq(ActivityVersion::getAuditStatus, VersionAuditStatus.PENDING_AUDIT));
        List<Long> activityIds = pendingVersions.stream()
                .map(ActivityVersion::getActivityId)
                .distinct()
                .toList();

        if (activityIds.isEmpty()) {
            return List.of();
        }

        // Paginate activities by those IDs
        IPage<Activity> page = activityMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Activity>()
                        .in(Activity::getId, activityIds)
                        .orderByDesc(Activity::getCreatedAt));

        return page.getRecords().stream()
                .map(act -> toResponse(act, findLatestVersion(act.getId())))
                .toList();
    }

    // -------------------------------------------------------
    // 7. Participate
    // -------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityResponse participate(Long activityId, Long userId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        if (activity.getStatus() != ActivityStatus.ONGOING.getCode()) {
            throw new BizException(ErrorCode.ACTIVITY_STATUS_INVALID, "活动未在进行中，不可参与");
        }

        // Duplicate check
        Long count = participationMapper.selectCount(
                new LambdaQueryWrapper<ActivityParticipation>()
                        .eq(ActivityParticipation::getActivityId, activityId)
                        .eq(ActivityParticipation::getUserId, userId));
        if (count > 0) {
            throw new BizException(ErrorCode.ACTIVITY_DUPLICATE_PARTICIPATION);
        }

        ActivityParticipation participation = new ActivityParticipation();
        participation.setActivityId(activityId);
        participation.setUserId(userId);
        participation.setParticipationType(activity.getType());
        participation.setRewardAmount(BigDecimal.ZERO);
        participation.setStatus(1);
        participationMapper.insert(participation);

        log.info("User {} participated in activity {}", userId, activityId);
        return toResponse(activity, findLatestVersion(activityId));
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private ActivityResponse toResponse(Activity activity, ActivityVersion activityVersion) {
        ActivityResponse resp = new ActivityResponse();
        resp.setId(activity.getId());
        resp.setActivityCode(activity.getActivityCode());
        resp.setTitle(activity.getTitle());
        resp.setType(activity.getType());
        resp.setStatus(activity.getStatus());
        resp.setStartTime(activity.getStartTime());
        resp.setEndTime(activity.getEndTime());
        resp.setBudgetAmount(activity.getBudgetAmount());
        resp.setBudgetUsed(activity.getBudgetUsed());
        resp.setRuleConfig(activity.getRuleConfig());
        resp.setCreatedAt(activity.getCreatedAt());
        resp.setOnlineVersionId(activity.getOnlineVersionId());
        resp.setCurrentVersion(activityVersion != null ? activityVersion.getVersion() : 0);
        resp.setVersionStatus(resolveVersionStatus(activityVersion));
        return resp;
    }

    private String resolveVersionStatus(ActivityVersion version) {
        if (version == null) {
            return "ONLINE";
        }
        int auditStatus = version.getAuditStatus();
        if (auditStatus == VersionAuditStatus.DRAFT) return "DRAFT";
        if (auditStatus == VersionAuditStatus.PENDING_AUDIT) return "PENDING_AUDIT";
        if (auditStatus == VersionAuditStatus.REJECTED) return "REJECTED";
        return "ONLINE"; // APPROVED + is_online=1, no pending edits
    }

    private ActivityVersion findOnlineVersion(Activity activity) {
        if (activity.getOnlineVersionId() != null) {
            return versionMapper.selectById(activity.getOnlineVersionId());
        }
        // Fallback: find is_online=1 version
        return versionMapper.selectOne(
                new LambdaQueryWrapper<ActivityVersion>()
                        .eq(ActivityVersion::getActivityId, activity.getId())
                        .eq(ActivityVersion::getIsOnline, 1)
                        .last("LIMIT 1"));
    }

    /** Find the latest version (by version DESC) regardless of online/audit status. */
    private ActivityVersion findLatestVersion(Long activityId) {
        return versionMapper.selectOne(
                new LambdaQueryWrapper<ActivityVersion>()
                        .eq(ActivityVersion::getActivityId, activityId)
                        .orderByDesc(ActivityVersion::getVersion)
                        .last("LIMIT 1"));
    }

    private void updateVersionContent(ActivityVersion version, ActivityUpdateRequest request) {
        VersionContent content = parseContent(version.getContent());
        content.mergeFrom(request);
        version.setContent(toJson(content));
    }

    private void applyContentToActivity(Activity activity, VersionContent content) {
        activity.setTitle(content.getTitle());
        activity.setStartTime(content.getStartTime());
        activity.setEndTime(content.getEndTime());
        activity.setBudgetAmount(content.getBudgetAmount());
        activity.setRuleConfig(content.getRuleConfig());
    }

    private VersionContent parseContent(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return new VersionContent();
        }
        try {
            return objectMapper.readValue(contentJson, VersionContent.class);
        } catch (Exception e) {
            log.error("Failed to parse version content: {}", contentJson, e);
            throw new BizException(ErrorCode.ACTIVITY_EVENT_DATA_PARSE_ERROR,
                    "活动版本内容解析失败: " + e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "JSON序列化失败: " + e.getMessage());
        }
    }

    // -------------------------------------------------------
    // Version audit status constants
    // -------------------------------------------------------

    /** Version audit status constants (column: audit_status) */
    static final class VersionAuditStatus {
        static final int DRAFT = 0;
        static final int PENDING_AUDIT = 1;
        static final int APPROVED = 2;
        static final int REJECTED = 3;
    }

    // -------------------------------------------------------
    // VersionContent — JSON structure stored in activity_version.content
    // -------------------------------------------------------

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class VersionContent {

        @JsonProperty("title")
        private String title;

        @JsonProperty("startTime")
        private LocalDateTime startTime;

        @JsonProperty("endTime")
        private LocalDateTime endTime;

        @JsonProperty("budgetAmount")
        private BigDecimal budgetAmount;

        @JsonProperty("ruleConfig")
        private String ruleConfig;

        static VersionContent from(ActivityCreateRequest request) {
            VersionContent c = new VersionContent();
            c.setTitle(request.getTitle());
            c.setStartTime(request.getStartTime());
            c.setEndTime(request.getEndTime());
            c.setBudgetAmount(request.getBudgetAmount());
            c.setRuleConfig(request.getRuleConfig());
            return c;
        }

        /**
         * Merge non-null fields from the update request into this content.
         */
        void mergeFrom(ActivityUpdateRequest request) {
            if (request.getTitle() != null) {
                this.title = request.getTitle();
            }
            if (request.getStartTime() != null) {
                this.startTime = request.getStartTime();
            }
            if (request.getEndTime() != null) {
                this.endTime = request.getEndTime();
            }
            if (request.getBudgetAmount() != null) {
                this.budgetAmount = request.getBudgetAmount();
            }
            if (request.getRuleConfig() != null) {
                this.ruleConfig = request.getRuleConfig();
            }
        }
    }
}
