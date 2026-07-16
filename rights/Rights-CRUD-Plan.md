# P3 权益发放系统 (rights-svc) — 完整实现方案

## 一、背景

rights-svc 现有完整 scaffold（Entity、DTO、Enum、Mapper、Controller、FeignClient）但所有 Service 方法均为 `UnsupportedOperationException("Not implemented yet")` 占位。现需实现四大功能：**权益定义管理**、**凭证生成与发放**、**供应商对接框架**、**过期处理**。

### 现状分析

**已有资产（无需修改）**：
- 3 个 Entity：`RightsDefinition`、`RightsInstance`、`RightsIssueLog`（均含 `@TableLogic`）
- 3 个 Enum：`RightsType`(5种)、`RightsInstanceStatus`(5态)、`RightsSourceType`(3源)
- 3 个 Mapper：均继承 `BaseMapper<Entity>`，无自定义方法
- 2 个 Controller：`RightsDefinitionController`(4端点)、`RightsInstanceController`(3端点)
- 1 个 FeignClient：`RightsFeignClient`
- `MybatisPlusConfig`：分页插件 + createTime/updateTime 自动填充
- 时间字段命名：`createTime`/`updateTime`（非 activity 模块的 `createdAt`/`updatedAt`）

---

## 二、核心设计

### 2.1 权益实例状态机

```
PENDING_ACTIVATE(0) → ACTIVATED(1) → USED(2)
                            ↓              ↓
                        EXPIRED(3)    REVOKED(4)
```

- 发放时：`status=PENDING_ACTIVATE`，`expireTime = now + rightsDefinition.validDays`
- 激活时（用户领取/确认）：`PENDING_ACTIVATE → ACTIVATED`
- 使用时：`ACTIVATED → USED`
- 过期：`ACTIVATED → EXPIRED`（定时任务自动处理）
- 撤销：`ACTIVATED → REVOKED`（需回滚库存）

### 2.2 凭证号生成规则

```
instanceNo = "R" + yyyyMMddHHmmss + 6位随机数字
例: R20260713143000123456
```

### 2.3 权益定义状态

```
status: 0 = 禁用, 1 = 启用
```

只有启用状态的定义才能发放。停用不影响已发放的实例。

### 2.4 幂等性设计

- `rights_instance.source_no` 建唯一索引
- 发放时先检查 `sourceNo` 是否已存在，存在则直接返回已有实例
- 与积分发放（`point_record.biz_no` 唯一索引）模式一致

### 2.5 供应商调用方式

外部供应商采用**同步重试**：调用时同步等待，最多重试 3 次（间隔 1s/3s/5s），全部失败后记录错误日志。实例仍然创建成功（后续人工处理）。

---

## 三、数据模型

### 3.1 现有表（DDL 待创建）

```sql
-- 权益定义
CREATE TABLE IF NOT EXISTS rights_definition (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rights_code     VARCHAR(64) NOT NULL UNIQUE COMMENT '权益编码',
    name            VARCHAR(128) NOT NULL COMMENT '权益名称',
    type            TINYINT NOT NULL COMMENT '1优惠券 2利率券 3VIP服务 4实物 5第三方权益',
    supplier_type   TINYINT NOT NULL COMMENT '0自有 1外部',
    supplier_code   VARCHAR(64) COMMENT '供应商编码',
    total_stock     INT NOT NULL COMMENT '总库存',
    used_stock      INT NOT NULL DEFAULT 0 COMMENT '已用库存',
    valid_days      INT NOT NULL COMMENT '有效天数(发放后)',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
    callback_url    VARCHAR(256) COMMENT '供应商回调地址',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权益定义';

-- 权益凭证（发放实例）
CREATE TABLE IF NOT EXISTS rights_instance (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    instance_no       VARCHAR(64) NOT NULL UNIQUE COMMENT '凭证号',
    rights_code       VARCHAR(64) NOT NULL,
    user_id           VARCHAR(64) NOT NULL,
    source_type       TINYINT NOT NULL COMMENT '1活动 2积分兑换 3手动发放',
    source_no         VARCHAR(128) COMMENT '来源单号',
    status            TINYINT NOT NULL DEFAULT 0 COMMENT '0待激活 1已激活 2已使用 3已过期 4已作废',
    activate_time     DATETIME COMMENT '激活时间',
    expire_time       DATETIME COMMENT '过期时间',
    use_time          DATETIME COMMENT '使用时间',
    supplier_order_no VARCHAR(128) COMMENT '供应商订单号',
    create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT NOT NULL DEFAULT 0,
    UNIQUE INDEX uk_source_no (source_no),
    INDEX idx_user_status (user_id, status),
    INDEX idx_expire (expire_time, status),
    INDEX idx_source (source_type, source_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权益凭证';

-- 权益发放日志
CREATE TABLE IF NOT EXISTS rights_issue_log (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    instance_no      VARCHAR(64) NOT NULL,
    rights_code      VARCHAR(64) NOT NULL,
    user_id          VARCHAR(64) NOT NULL,
    source_type      TINYINT NOT NULL COMMENT '1活动 2积分兑换 3手动发放',
    source_no        VARCHAR(128) COMMENT '来源单号',
    operation_type   TINYINT NOT NULL COMMENT '1发放 2激活 3使用 4过期 5撤销',
    operation_result TINYINT NOT NULL COMMENT '0失败 1成功',
    retry_count      INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    error_msg        VARCHAR(512) COMMENT '错误信息',
    remark           VARCHAR(256) COMMENT '备注',
    create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          TINYINT NOT NULL DEFAULT 0,
    INDEX idx_status_retry (operation_result, retry_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权益发放日志';
```

### 3.2 Entity 增补字段

**RightsInstance** 新增：
- `supplierOrderNo`：供应商返回的订单号

**RightsIssueLog** 新增：
- `retryCount`：重试次数
- `errorMsg`：错误信息

---

## 四、供应商对接框架（策略模式）

```java
public interface SupplierStrategy {
    Integer supplierType();  // 0=自有, 1=外部
    boolean issue(RightsDefinition definition, RightsInstance instance);
}
```

### InternalSupplierStrategy（supplierType=0）
- 直接 return true（自有权益无需外部调用）

### ExternalSupplierStrategy（supplierType=1）
- 使用 RestTemplate POST 到 `definition.callbackUrl`
- Request body：`{instanceNo, rightsCode, userId}`
- 同步重试 3 次（间隔 1s / 3s / 5s）
- 成功 → 记录 RightsIssueLog（result=1）
- 全部失败 → 记录 RightsIssueLog（result=0, errorMsg），不阻塞返回（实例已创建，人工处理）

### SupplierContext（策略注册表）
- 自动发现所有 `SupplierStrategy` 实现，按 `supplierType` 索引
- `getStrategy(Integer supplierType)` 返回对应策略

---

## 五、改动范围（15 个文件）

### 5.1 增补已有 Entity 字段（2 个文件）

| 文件 | 操作 | 说明 |
|------|------|------|
| `rights-domain/.../entity/RightsInstance.java` | 修改 | 新增 `supplierOrderNo` 字段 |
| `rights-domain/.../entity/RightsIssueLog.java` | 修改 | 新增 `retryCount`、`errorMsg` 字段 |

### 5.2 新增 DTO（2 个文件）

| 文件 | 操作 | 说明 |
|------|------|------|
| `rights-api/.../dto/RightsDefinitionUpdateRequest.java` | **新建** | 更新请求 DTO（所有字段可选） |
| `rights-api/.../dto/RightsIssueResponse.java` | **新建** | 发放响应 DTO（含 instanceNo、status、supplierOrderNo） |

### 5.3 修改 ErrorCode（1 个文件）

| 文件 | 操作 | 新增错误码 |
|------|------|------|
| `common/.../model/ErrorCode.java` | 修改 | RIGHTS_INSTANCE_NOT_FOUND("40005")、RIGHTS_STATUS_INVALID("40006")、RIGHTS_DEFINITION_DISABLED("40007")、RIGHTS_SUPPLIER_ERROR("40008") |

### 5.4 新增基础设施（2 个文件）

| 文件 | 操作 | 说明 |
|------|------|------|
| `rights-infrastructure/.../config/RedissonConfig.java` | **新建** | RedissonClient Bean，参考 point 模块 |
| `rights-infrastructure/.../util/DistributedLockHelper.java` | **新建** | lock key: `rights:lock:{rightsCode}:{userId}` |

### 5.5 供应商策略（3 个文件）

| 文件 | 操作 | 说明 |
|------|------|------|
| `rights-application/.../strategy/SupplierStrategy.java` | **新建** | 策略接口 |
| `rights-application/.../strategy/SupplierContext.java` | **新建** | 策略注册表 |
| `rights-application/.../strategy/ExternalSupplierStrategy.java` | **新建** | 外部供应商实现（RestTemplate + 重试） |

### 5.6 服务实现（4 个文件）

| 文件 | 操作 | 说明 |
|------|------|------|
| `rights-application/.../service/RightsDefinitionService.java` | 修改 | 增加 enable/disable 方法签名 |
| `rights-application/.../service/RightsIssueService.java` | 修改 | 增加 activate/use/revoke 方法签名 |
| `rights-application/.../service/impl/RightsDefinitionServiceImpl.java` | 重写 | 完整 CRUD 实现 |
| `rights-application/.../service/impl/RightsIssueServiceImpl.java` | 重写 | 完整发放 + 状态流转实现 |

### 5.7 定时任务（1 个文件）

| 文件 | 操作 | 说明 |
|------|------|------|
| `rights-boot/.../task/RightsExpireTask.java` | **新建** | 每小时扫描过期实例，批量标记 |

### 5.8 启动类 + SQL（2 个文件）

| 文件 | 操作 | 说明 |
|------|------|------|
| `rights-boot/.../BootApplication.java` | 修改 | 加 `@EnableScheduling`、扩展 `scanBasePackages` 到 common |
| `docker/mysql/init/04-init-rights-tables.sql` | **新建** | 3 张表的 DDL（幂等），含 `source_no` UNIQUE KEY |

---

## 六、方法实现详述

### 6.1 RightsDefinitionServiceImpl

#### `create(RightsDefinitionCreateRequest)` → RightsDefinitionResponse
1. 校验 name 非空、totalStock > 0、validDays > 0
2. 生成 rightsCode: `RGTS` + yyyyMMdd + 4位随机数字
3. 保存 RightsDefinition（status=1 启用）
4. 返回 RightsDefinitionResponse

#### `update(Long id, RightsDefinitionUpdateRequest)` → RightsDefinitionResponse
1. 查 RightsDefinition，不存在抛 RIGHTS_NOT_FOUND
2. 合并 updateRequest 中非 null 字段
3. 更新后返回

#### `getById(Long id)` → RightsDefinitionResponse
1. 查 RightsDefinition，不存在抛 RIGHTS_NOT_FOUND
2. 返回 Response

#### `page(pageNum, pageSize, type)` → List\<RightsDefinitionResponse\>
1. LambdaQueryWrapper 按 type 筛选（可选），createTime 倒序
2. 分页返回

#### `enable(Long id)` → void
1. 查 RightsDefinition，status = 1，更新

#### `disable(Long id)` → void
1. 查 RightsDefinition，status = 0，更新

### 6.2 RightsIssueServiceImpl

#### `issue(RightsIssueRequest)` → RightsIssueResponse
1. **幂等检查**：若 request.sourceNo 非空，查 `rights_instance` 表 `source_no = sourceNo`，已存在则直接返回已有实例
2. 查 RightsDefinition，不存在抛 RIGHTS_NOT_FOUND
3. 校验 status==1（启用），否则抛 RIGHTS_DEFINITION_DISABLED
4. **库存预扣**：`usedStock + 1 <= totalStock`，原子更新 `usedStock = usedStock + 1`
   - 用 `LambdaUpdateWrapper.setSql("used_stock = used_stock + 1").eq("used_stock + 1 <= total_stock")`
   - 更新行数=0 则抛 RIGHTS_STOCK_INSUFFICIENT
5. **生成凭证**：instanceNo = "R" + yyyyMMddHHmmss + 6位随机数
6. **计算过期时间**：expireTime = LocalDateTime.now() + validDays
7. 保存 RightsInstance（status=PENDING_ACTIVATE，sourceType/sourceNo 用于幂等）
8. **调用供应商**（同步阻塞）：通过 SupplierContext 路由到对应 SupplierStrategy
9. **记录日志**：RightsIssueLog（operationType=1发放, operationResult, retryCount, errorMsg）
10. 返回 RightsIssueResponse

#### `getByInstanceNo(String instanceNo)` → RightsInstanceResponse
1. 查 RightsInstance，不存在抛 RIGHTS_INSTANCE_NOT_FOUND
2. 返回 Response

#### `getByUserId(String userId)` → List\<RightsInstanceResponse\>
1. 查 RightsInstance 列表（按 userId，创建时间倒序）
2. 返回 Response 列表

#### `activate(String instanceNo)` → RightsInstanceResponse
1. 查 RightsInstance，校验 status==PENDING_ACTIVATE
2. status → ACTIVATED, activateTime = now
3. 记录日志：operationType=2激活, operationResult=1
4. 更新，返回

#### `use(String instanceNo)` → RightsInstanceResponse
1. 查 RightsInstance，校验 status==ACTIVATED 且 expireTime > now
2. status → USED, useTime = now
3. 记录日志：operationType=3使用, operationResult=1
4. 更新，返回

#### `revoke(String instanceNo)` → RightsInstanceResponse
1. 查 RightsInstance，校验 status==ACTIVATED
2. status → REVOKED
3. **回滚库存**：`usedStock = usedStock - 1`（RightsDefinition）
4. 记录日志：operationType=5撤销, operationResult=1
5. 更新，返回

### 6.3 RightsExpireTask

- `@Scheduled(cron = "0 10 * * * ?")` — 每小时第10分钟执行（与 point expire 的 minute 5 错开）
- 分批扫描（200条/批）：`status=ACTIVATED AND expire_time < now`
- 每批处理：status → EXPIRED
- 写 RightsIssueLog（operationType=4过期, operationResult=1）
- 无需分布式锁（只更新实例状态，不涉及账户金额变更）

---

## 七、API 接口一览

```
# 权益定义管理
POST   /api/v1/rights/definitions                    创建权益定义
PUT    /api/v1/rights/definitions/{id}                更新权益定义
GET    /api/v1/rights/definitions/{id}                查询权益定义
GET    /api/v1/rights/definitions/page                分页查询权益定义
PUT    /api/v1/rights/definitions/{id}/enable         启用
PUT    /api/v1/rights/definitions/{id}/disable        停用

# 权益实例管理
POST   /api/v1/rights/instances/issue                 发放权益
GET    /api/v1/rights/instances/getByInstanceNo        查询凭证
GET    /api/v1/rights/instances/getByUserId            查询用户权益
PUT    /api/v1/rights/instances/{instanceNo}/activate  激活
PUT    /api/v1/rights/instances/{instanceNo}/use       使用
PUT    /api/v1/rights/instances/{instanceNo}/revoke    撤销
```

---

## 八、验证

1. 编译：`mvn clean compile -pl rights -am -DskipTests`
2. 构建：先 `mvn clean install -f common/pom.xml -DskipTests`，再 `mvn clean install -f rights/pom.xml -DskipTests`
3. 启动：`mvn spring-boot:run -pl rights/rights-boot -DskipTests`
4. API 测试：
   - 创建权益定义 → 发放 → 激活 → 使用 → 查询用户权益
   - 库存耗尽 → 发放返回 RIGHTS_STOCK_INSUFFICIENT
   - 停用定义 → 发放返回 RIGHTS_DEFINITION_DISABLED
   - 外部供应商失败 → 实例仍然创建，日志记录错误
   - 定时任务 → 过期实例状态变更为 EXPIRED
