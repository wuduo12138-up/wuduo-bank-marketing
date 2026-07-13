# P2 活动系统 (Activity System) 实现计划

## Context

在已有 P1 积分系统的基础上，实现活动系统的核心能力。支持三种触发模式的活动：单次触发、积累触发、月度累计达标。活动系统接收外部事件后，根据活动的 ruleConfig 配置判断用户是否达标，达标后调用积分服务发放奖励。

现有 activity 模块只有骨架代码（6 个 service 方法全部是 `UnsupportedOperationException`），基础设施（MyBatis-Plus、Redis、Redisson、Nacos、Druid）已配置好。

## 三种活动类型

### 类型 1：SINGLE_TRIGGER（单次触发）
- 外部上报事件 → 检查用户在当前周期内是否已完成 → 未完成则发奖
- 可配置频率限制：如每月 1 次
- 示例：企业大额交易一次即达标
- ruleConfig：
```json
{"triggerType":"SINGLE_TRIGGER","eventType":"LARGE_TRANSACTION","frequency":{"period":"MONTHLY","maxCount":1},"reward":{"type":"POINTS","amount":1000}}
```

### 类型 2：ACCUMULATION_TRIGGER（积累触发）
- 外部上报事件 → 累加计数器（不归零）→ floor(累计值/阈值) 决定达标次数 → 受频率上限限制
- 计数器不重置，以总量计算。如阈值 100、每月最多 2 次：100 次→1 次达标→200 次→2 次达标→300 次→无新达标（已达月上限）
- ruleConfig：
```json
{"triggerType":"ACCUMULATION_TRIGGER","eventType":"INVOICE","threshold":100,"frequency":{"period":"MONTHLY","maxCount":2},"reward":{"type":"POINTS","amount":500}}
```

### 类型 3：MONTHLY_CRITERIA（每月累计达标）
- 每月 1 号凌晨定时任务执行
- 调用外部服务查询达标用户列表 → 逐个发奖
- ruleConfig：
```json
{"triggerType":"MONTHLY_CRITERIA","criteriaService":"transaction-service","criteriaEndpoint":"/api/internal/qualified-users","reward":{"type":"POINTS","amount":2000}}
```

## 事件来源说明

用户期望走消息队列，但 P2 先通过 REST API 接入（`POST /api/v1/activities/events`），MQ 集成（RocketMQ）作为 P3 增强。当前方案：外部系统通过其自身的 MQ consumer 收到消息后调用活动系统的 REST API。这样 P2 可立即验证业务逻辑，P3 只需在 activity-boot 增加 MQ consumer 层，内部 strategy 逻辑不变。

## 数据库 DDL

```sql
-- 新表：用户活动进度
CREATE TABLE activity_user_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    current_value BIGINT NOT NULL DEFAULT 0 COMMENT '累计值（积累类型用）',
    period_key VARCHAR(16) NOT NULL COMMENT '周期标识，如 2026-07',
    completion_count INT NOT NULL DEFAULT 0 COMMENT '当前周期内已完成次数',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_activity_user_period (activity_id, user_id, period_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户活动进度';

-- 修改已有表
ALTER TABLE activity_participation
    ADD COLUMN biz_no VARCHAR(64) DEFAULT NULL COMMENT '业务流水号(幂等)',
    ADD COLUMN period_key VARCHAR(16) DEFAULT NULL COMMENT '周期标识',
    ADD INDEX idx_activity_user_bizno (activity_id, user_id, biz_no);

-- 创建 activity 业务表（如果不存在的话）
-- 参考 docker/mysql/init/02-init-point-tables.sql 的风格
```

## 新增文件清单（16 个）

### activity-api（5 个）

| # | 文件 | 说明 |
|---|------|------|
| 1 | `api/enums/TriggerType.java` | SINGLE_TRIGGER(1), ACCUMULATION_TRIGGER(2), MONTHLY_CRITERIA(3) |
| 2 | `api/enums/FrequencyPeriod.java` | DAILY/WEEKLY/MONTHLY/YEARLY/LIFETIME，含 generatePeriodKey() 方法 |
| 3 | `api/dto/RuleConfig.java` | ruleConfig JSON 对应的 POJO，含嵌套 FrequencyConfig, RewardConfig |
| 4 | `api/dto/EventReportRequest.java` | eventType, userId, bizNo, eventData |
| 5 | `api/dto/EventReportResponse.java` | activityId, completionsAwarded, rewardAmount, skipped |

### activity-domain（1 个）

| # | 文件 | 说明 |
|---|------|------|
| 6 | `domain/entity/ActivityUserProgress.java` | 对应 activity_user_progress 表，字段 id/activityId/userId/currentValue/periodKey/completionCount/version |

### activity-infrastructure（3 个）

| # | 文件 | 说明 |
|---|------|------|
| 7 | `infrastructure/mapper/ActivityUserProgressMapper.java` | MyBatis-Plus BaseMapper |
| 8 | `infrastructure/config/RedissonConfig.java` | 从 point 模块复制，创建 RedissonClient bean |
| 9 | `infrastructure/util/DistributedLockHelper.java` | 从 point 模块复制，lockKey 改为 `activity:lock:` 前缀，支持 (activityId, userId) 复合锁 |

### activity-application（5 个）

| # | 文件 | 说明 |
|---|------|------|
| 10 | `application/service/EventService.java` | processEvent(EventReportRequest) 接口 |
| 11 | `application/service/impl/EventServiceImpl.java` | 事件处理编排：查匹配活动→解析 ruleConfig→分派 strategy→发奖励 |
| 12 | `application/service/strategy/ActivityTriggerStrategy.java` | 策略接口：supportedType(), processEvent() |
| 13 | `application/service/strategy/ActivityTriggerContext.java` | Spring 注入所有 strategy 实现，按 TriggerType 路由 |
| 14 | `application/service/strategy/SingleTriggerStrategy.java` | 单次触发逻辑：幂等检查→周期检查→发奖励→更新进度 |
| 15 | `application/service/strategy/AccumulationTriggerStrategy.java` | 积累触发逻辑：幂等检查→累加计数器→floor(值/阈值)→发 Δ 奖励→更新完成次数 |

### activity-boot（1 个）

| # | 文件 | 说明 |
|---|------|------|
| 16 | `boot/task/MonthlyCriteriaTask.java` | @Scheduled(cron="0 0 1 1 * ?") 每月 1 号凌晨：查 MONTHLY_CRITERIA 活动→调外部服务→逐用户加锁发奖 |

## 修改文件清单（7 个）

| # | 文件 | 修改内容 |
|---|------|----------|
| 17 | `common/.../model/ErrorCode.java` | 新增 7 个错误码（10006-10012） |
| 18 | `activity/activity-api/.../enums/ActivityType.java` | 新增 EVENT_DRIVEN(5), MONTHLY_CRITERIA(6) |
| 19 | `activity/activity-domain/.../entity/ActivityParticipation.java` | 新增 bizNo, periodKey 字段 |
| 20 | `activity/activity-infrastructure/pom.xml` | 新增 redisson-spring-boot-starter 依赖 |
| 21 | `activity/activity-application/pom.xml` | 新增 point-api 依赖 |
| 22 | `activity/activity-boot/.../BootApplication.java` | 新增 @EnableScheduling；@EnableFeignClients 增加 point-api 扫描路径 |
| 23 | `activity/activity-infrastructure/.../config/MybatisPlusConfig.java` | 新增 MetaObjectHandler（auto-fill createdAt/updatedAt） |

## 新增错误码

```java
ACTIVITY_EVENT_DUPLICATE("10006", "重复的事件"),
ACTIVITY_FREQUENCY_EXCEEDED("10007", "活动频率已达上限"),
ACTIVITY_NOT_ONGOING("10008", "活动未在进行中"),
ACTIVITY_TRIGGER_TYPE_INVALID("10009", "无效的触发器类型"),
ACTIVITY_CRITERIA_SERVICE_ERROR("10010", "外部达标服务调用失败"),
ACTIVITY_PROGRESS_NOT_FOUND("10011", "活动进度记录不存在"),
ACTIVITY_REWARD_FAILED("10012", "活动奖励发放失败");
```

## 架构设计

### 事件处理流程

```
外部系统 → POST /api/v1/activities/events {eventType, userId, bizNo}
  → EventController
    → EventServiceImpl.processEvent() [@Transactional]
      1. 参数校验
      2. 查 ONGOING + EVENT_DRIVEN 类型的活动
      3. 解析每个活动的 ruleConfig JSON，匹配 eventType
      4. 获取对应 TriggerType 的 strategy
      5. 分布式锁 (activity:lock:{activityId}:{userId})
         → strategy.processEvent()
           [SINGLE] 频率检查 → 发奖 → 进度+1
           [ACCUMULATION] 累加计数 → 计算达标次数 → 差额发奖
      6. 返回 EventReportResponse
```

### 策略模式

```
ActivityTriggerStrategy (接口)
  ├── SingleTriggerStrategy      → 处理 SINGLE_TRIGGER
  ├── AccumulationTriggerStrategy → 处理 ACCUMULATION_TRIGGER
  └── (MonthlyCriteria 不走事件线，由定时任务直接处理)

ActivityTriggerContext → 持有 Map<TriggerType, Strategy>，Spring 自动注入
```

### 关键算法：ACCUMULATION_TRIGGER 达标计算

```java
// 1. 累加计数器（乐观锁）
progress.currentValue += 1;

// 2. 计算达标次数（不归零，以总量计算）
int earned = (int)(progress.currentValue / threshold);

// 3. 受频率上限限制
int capped = Math.min(earned, frequencyMaxCount);

// 4. 差额 = 应达标 - 已达标（本次事件新增的达标次数）
int delta = capped - progress.completionCount;

// 5. 超出频率上限的部分不发奖
if (delta <= 0) return 0;

// 6. 发放 delta 次奖励
for (int i = 0; i < delta; i++) { issuePointsReward(...); }
```

### 乐观锁重试（与 point 模块一致的显式写法）

```java
LambdaUpdateWrapper<ActivityUserProgress> wrapper = new LambdaUpdateWrapper<>();
wrapper.eq(ActivityUserProgress::getId, progress.getId())
       .eq(ActivityUserProgress::getVersion, currentVersion)
       .set(ActivityUserProgress::getVersion, currentVersion + 1)
       .set(ActivityUserProgress::getCurrentValue, progress.getCurrentValue())
       .set(ActivityUserProgress::getCompletionCount, progress.getCompletionCount());
int updated = progressMapper.update(null, wrapper);
```

### 积分发放（调用 PointFeignClient）

```java
PointIssueRequest issueReq = new PointIssueRequest();
issueReq.setUserId(userId);
issueReq.setPointAmount(amount);
issueReq.setType(ACTIVITY_EARN(1));    // PointRecordType.ACTIVITY_EARN
issueReq.setBizSource("ACTIVITY_" + activityId);
issueReq.setBizNo(bizNo);             // 幂等：同一 bizNo 不重复发
pointFeignClient.issue(issueReq);
```

## 实现顺序

| 阶段 | 步骤 | 内容 |
|------|------|------|
| 1. 基础 | 错误码、枚举（TriggerType, FrequencyPeriod）、DTO（RuleConfig, EventReport*） | 0 依赖 |
| 2. 领域+基础设施 | Entity（ActivityUserProgress）、修改 ActivityParticipation、Mapper、RedissonConfig、DistributedLockHelper、pom.xml 依赖更新 | 依赖阶段 1 |
| 3. 策略层 | ActivityTriggerStrategy 接口、ActivityTriggerContext、SingleTriggerStrategy、AccumulationTriggerStrategy | 依赖阶段 2 |
| 4. 服务层 | EventService 接口、EventServiceImpl 实现 | 依赖阶段 3 |
| 5. 启动层 | EventController、RestTemplateConfig、MonthlyCriteriaTask、修改 BootApplication 和 ActivityType | 依赖阶段 4 |
| 6. DDL | SQL 建表/改表脚本 → docker/mysql/init/03-init-activity-tables.sql | 可并行 |
| 7. 编译验证 | `mvn clean compile -DskipTests` | 依赖阶段 1-5 |
| 8. 启动测试 | docker-compose up → 启动 activity 服务 → API 测试 | 依赖阶段 6-7 |

## 验证方式

1. **SINGLE_TRIGGER 测试**
   - POST 事件 bizNo=E1 → 发奖成功，completionCount=1
   - POST 同一 bizNo=E1 → 幂等跳过
   - POST 新 bizNo=E2 → 频率超限报错（同周期同用户）

2. **ACCUMULATION_TRIGGER 测试**
   - POST 99 次事件 → 0 次达标，currentValue=99
   - POST 第 100 次 → 1 次达标，completionCount=1
   - POST 第 200 次 → 2 次达标
   - POST 第 300 次 → 0 次新达标（达月上限 2）

3. **MONTHLY_CRITERIA 测试**
   - 配置 mock 外部服务返回 [userId:1, userId:2]
   - 手动触发定时任务 → 2 个用户各发奖 1 次
   - 再次触发 → 幂等跳过（已达标）

4. **并发测试**
   - 10 并发同一 (activityId, userId) 不同 bizNo → 分布式锁串行化，无重复发奖

5. **对账验证**
   - 查 ActivityParticipation 表确认 completion_count 与实际发奖次数一致
   - 查积分流水确认发放次数和金额匹配
