# P2 活动系统 - 活动后台 CRUD 实现计划

## 核心设计："草稿 + 版本"双轨制审批

### 表结构

**活动主表 `activity`**（已有，新增 online_version_id）：
- 保留现有全部字段作为当前线上版本的镜像/缓存
- 新增 `online_version_id` — 指向当前生效的版本记录 ID
- 已有字段（title/startTime/endTime/budgetAmount/ruleConfig 等）在审批通过时从版本 content 同步过来

**活动版本表 `activity_version`**（新建）：

```sql
CREATE TABLE IF NOT EXISTS activity_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 1 COMMENT '版本号，自增',
    content TEXT NOT NULL COMMENT '活动完整配置快照(JSON)',
    audit_status TINYINT NOT NULL DEFAULT 0 COMMENT '审批状态: 0=草稿, 1=待审批, 2=已通过, 3=已驳回',
    is_online TINYINT NOT NULL DEFAULT 0 COMMENT '是否线上版本: 0=否, 1=是',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT NULL,
    INDEX idx_activity_audit (activity_id, audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动版本表';
```

### content JSON 结构（全量快照）

```json
{
  "title": "活动标题",
  "startTime": "2026-07-01T00:00:00",
  "endTime": "2026-12-31T23:59:59",
  "budgetAmount": 100000,
  "ruleConfig": { "triggerType": "ACCUMULATION_TRIGGER", ... }
}
```

### 完整流程

```
【初始创建】
运营 → create [创建 activity (DRAFT) + 创建 V1 (草稿)]
运营 → update(V1) [编辑 V1 草稿内容，可多次修改]
运营 → audit(approved=true) [V1: 草稿→通过, is_online=true, activity: DRAFT→PUBLISHED]

【活动上线后修改】
运营 → update [从线上 V1 深拷贝 → 创建 V2 (草稿), activity 线上数据不变]
运营 → audit [V2: 草稿→通过, V1.is_online=false, V2.is_online=true, activity 同步 V2 内容]
```

### 关键规则

| 场景 | activity.status | version.audit_status | 行为 |
|------|:---:|:---:|------|
| 新建活动 | DRAFT | V1=草稿(0) | create 同时创建 activity + V1 |
| DRAFT 编辑 | DRAFT | V1=草稿(0) | 直接修改 V1.content |
| 提交审核 | → PENDING_AUDIT | → 待审批(1) | update 中 status 流转 |
| 初始审核通过 | → PUBLISHED | → 已通过(2), is_online=true | activity 从 V1.content 同步配置 |
| 初始审核驳回 | → REJECTED | → 已驳回(3) | V1.content 保留，可再编辑 |
| ONGOING 编辑 | ONGOING(不变) | 新 Vn=草稿(0) | 从线上版本深拷贝创建新版本 |
| 编辑审核通过 | ONGOING(不变) | Vn→已通过, is_online=true | 旧版本 is_online=false, activity 同步 Vn.content |
| 编辑审核驳回 | ONGOING(不变) | Vn→已驳回(3) | 旧在线版本不变, Vn 内容保留可再编辑 |

## 改动范围（6 个文件）

| # | 文件 | 操作 |
|---|------|------|
| 1 | `domain/entity/Activity.java` | 新增 `onlineVersionId` 字段 |
| 2 | `domain/entity/ActivityVersion.java` | **新建** |
| 3 | `infrastructure/mapper/ActivityVersionMapper.java` | **新建** |
| 4 | `api/dto/ActivityResponse.java` | 新增 `onlineVersionId`, `currentVersion` |
| 5 | `application/.../impl/ActivityServiceImpl.java` | 完整实现 6 个方法 |
| 6 | `docker/mysql/init/03-init-activity-tables.sql` | 新增 activity_version 表 + ALTER activity |

## 6 个方法实现

### 1. `create(ActivityCreateRequest)` → ActivityResponse
1. 校验 startTime < endTime, budgetAmount > 0
2. 生成 activityCode: `ACT` + yyyyMMddHHmmss + 4位随机数字
3. 保存 Activity (status=DRAFT, onlineVersionId=null)
4. 生成内容 JSON（含 title/startTime/endTime/budgetAmount/ruleConfig）
5. 创建 ActivityVersion V1 (version=1, content=JSON, auditStatus=草稿, isOnline=false)
6. 返回 ActivityResponse

### 2. `update(ActivityUpdateRequest)` → ActivityResponse
1. 查 Activity + 查最新版本
2. **情况 A: 初始编辑**（status == DRAFT 或 REJECTED）
   - 直接修改最新版本（V1）的 content
   - 若传了 submit=true，status→PENDING_AUDIT, auditStatus→待审批(1)
3. **情况 B: 线上编辑**（status == PUBLISHED 或 ONGOING）
   - 从在线版本深拷贝 content → 合并修改 → 创建新版本行
   - activity 不变，线上不受影响
   - 若传了 submit=true，新版本 auditStatus→待审批(1)

### 3. `getById(Long id)` → ActivityResponse
- 查 Activity + 查在线版本获取 version
- 返回 ActivityResponse

### 4. `page(pageNum, pageSize, status, type)` → List\<ActivityResponse\>
- MyBatis-Plus Page 分页 + LambdaQueryWrapper 筛选
- 按 createdAt 倒序

### 5. `audit(Long id, Boolean approved)` → ActivityResponse
- 找到 auditStatus=待审批(1) 的版本
- **初始审核**（activity.status == PENDING_AUDIT）：通过→已通过(2), is_online=true, activity→PUBLISHED
- **编辑审核**（activity.status == ONGOING）：通过→旧版本 is_online=false, Vn is_online=true, 同步 activity
- 驳回→已驳回(3), 初始审核时 activity→REJECTED

### 6. `participate(Long activityId, Long userId)` → ActivityResponse
- 校验 ONGOING → 查重 → 创建 ActivityParticipation

## 验证

1. 编译：`mvn compile -pl activity -am -DskipTests`
2. API 测试：创建→编辑→提交审核→通过→线上编辑→审批→切换
3. 关键点：编辑 ONGOING 时线上数据不变；审批通过后 activity 同步新版本内容
