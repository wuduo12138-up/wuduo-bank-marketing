# 银行企业金融App营销系统 — 技术方案

## 一、系统总体架构

### 1.1 架构总览

```
                          ┌──────────────┐
                          │   Nginx/GW   │  统一网关(鉴权、限流、路由)
                          └──────┬───────┘
                                 │
              ┌──────────────────┼──────────────────┐
              │                  │                  │
     ┌────────▼────────┐ ┌──────▼───────┐ ┌───────▼────────┐
     │  activity-svc   │ │  point-svc   │ │ point-mall-svc │
     │   活动系统 :8081│ │  积分系统 :8082│ │ 积分商城  :8083│
     └────────┬────────┘ └──────┬───────┘ └───────┬────────┘
              │                  │                  │
     ┌────────▼────────┐        │         ┌───────▼────────┐
     │  rights-svc     │◄───────┘         │  (MQ消息总线)   │
     │  权益发放 :8084 │◄────────────────►│  RocketMQ       │
     └─────────────────┘                  └────────────────┘
              │                  │                  │
     ┌────────▼──────────────────▼──────────────────▼────────┐
     │              MySQL (主从) + Redis Cluster              │
     └───────────────────────────────────────────────────────┘
```

### 1.2 技术选型

| 层次 | 技术 | 说明 |
|------|------|------|
| 语言/框架 | Java 17 + Spring Boot 3.2 + Spring Cloud 2023 | 银行主流，生态成熟 |
| 构建 | Maven 3.9 + 多模块 | 每个微服务独立仓库，内部多模块 |
| 数据库 | MySQL 8.0 (主从) | 核心业务数据，强一致性 |
| 缓存 | Redis 7 Cluster | 热点数据、分布式锁、限流 |
| 消息队列 | RocketMQ 5.x | 事务消息、顺序消费、高可靠 |
| 注册/配置 | Nacos 2.x | 服务注册发现 + 动态配置 |
| 网关 | Spring Cloud Gateway | 鉴权、限流、路由 |
| 分布式事务 | Seata AT 模式 | 跨服务一致性保障 |
| 任务调度 | XXL-Job | 定时活动起停、过期清理 |
| 监控 | Prometheus + Grafana + SkyWalking | 指标 + 链路追踪 |
| CI/CD | GitHub Actions + Docker + K8s | 全容器化部署 |

### 1.3 各服务内部模块划分（DDD 分层）

以 activity 为例，所有服务遵循相同的模块结构：

```
activity/
├── activity-api              # DTO、Feign Client、常量（供其他服务依赖）
├── activity-domain           # 领域模型、领域服务
├── activity-infrastructure   # 持久化、MQ、Redis 实现
├── activity-application      # 应用服务、编排逻辑
└── activity-boot             # 启动模块、Controller、配置
```

---

## 二、四大核心模块业务流程与数据模型

### 2.1 活动系统 (activity-svc)

**核心职责**：活动的创建、审批、发布、参与、结算全生命周期管理。

**业务流程**：

```
创建活动 → 配置规则 → 风控审批 → 发布上线
                                    │
              ┌─────────────────────┤
              ▼                     ▼
         用户参与活动          活动触发事件
         (签到/交易/          (积分发放/
          邀请/答题)           权益发放)
              │                     │
              ▼                     ▼
         参与记录落库 ──MQ──→ point-svc / rights-svc
              │
              ▼
         活动结算(对账/统计)
```

**活动状态机**：

```
草稿(0) → 待审批(1) → 已发布(3) → 进行中(4) → 已结束(5)
              │             ↑
              ▼             │
          已驳回(2) ────────┘ (修改后重新提交)
                                        │
                                        ▼
                                    已取消(6)
```

**数据模型**：

```sql
-- 活动主表
CREATE TABLE activity (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_code   VARCHAR(64) NOT NULL UNIQUE COMMENT '活动编码',
    title           VARCHAR(128) NOT NULL COMMENT '活动标题',
    type            TINYINT NOT NULL COMMENT '类型:1签到 2交易 3邀请 4答题',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1待审批 2已驳回 3已发布 4进行中 5已结束 6已取消',
    start_time      DATETIME NOT NULL COMMENT '开始时间',
    end_time        DATETIME NOT NULL COMMENT '结束时间',
    budget_amount   DECIMAL(18,2) COMMENT '预算金额',
    budget_used     DECIMAL(18,2) DEFAULT 0 COMMENT '已用预算',
    rule_config     JSON NOT NULL COMMENT '活动规则配置(参与条件/奖励规则/频次限制)',
    audit_status    TINYINT DEFAULT 0 COMMENT '0未提交 1审批中 2通过 3驳回',
    auditor         VARCHAR(64) COMMENT '审批人',
    audit_remark    VARCHAR(512) COMMENT '审批意见',
    creator         VARCHAR(64) NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT NOT NULL DEFAULT 0,
    INDEX idx_status_time (status, start_time),
    INDEX idx_code (activity_code)
) ENGINE=InnoDB COMMENT='活动主表';

-- 活动参与记录
CREATE TABLE activity_participation (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_code   VARCHAR(64) NOT NULL,
    user_id         VARCHAR(64) NOT NULL COMMENT '用户ID(企业客户)',
    participation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    participation_data JSON COMMENT '参与数据(交易金额/答题结果等)',
    reward_status   TINYINT NOT NULL DEFAULT 0 COMMENT '0待发放 1已发放 2发放失败 3已撤回',
    reward_detail   JSON COMMENT '奖励明细',
    INDEX idx_user_activity (user_id, activity_code),
    INDEX idx_activity_time (activity_code, participation_time)
) ENGINE=InnoDB COMMENT='活动参与记录';

-- 活动预算流水(防超发)
CREATE TABLE activity_budget_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_code   VARCHAR(64) NOT NULL,
    amount          DECIMAL(18,2) NOT NULL,
    type            TINYINT NOT NULL COMMENT '1扣减 2回滚',
    biz_id          VARCHAR(128) NOT NULL COMMENT '业务单号',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_biz (biz_id)
) ENGINE=InnoDB COMMENT='活动预算流水';
```

**关键设计点**：
- 活动规则用 JSON 存储，支持灵活配置不同类型的参与条件和奖励规则
- 预算扣减通过 `activity_budget_log` 的唯一索引 + 乐观锁防超发
- 活动状态机：草稿→待审批→已发布→进行中→已结束，保证流转合法

---

### 2.2 积分系统 (point-svc)

**核心职责**：积分账户管理、积分发放/扣减、积分流水、过期清理。

**业务流程**：

```
活动系统/交易系统 ──MQ──→ 积分发放请求
                              │
                   ┌──────────┤
                   ▼          ▼
            预扣积分      规则引擎计算
            (Redis)      (倍数/上限/去重)
                   │          │
                   └────┬─────┘
                        ▼
               积分账户入账(MySQL)
                        │
                   ┌────┴────┐
                   ▼         ▼
            积分流水落库   过期时间写入
                        │
                        ▼
               定时任务扫描过期积分
               (XXL-Job批量软扣减)
```

**数据模型**：

```sql
-- 积分账户
CREATE TABLE point_account (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         VARCHAR(64) NOT NULL UNIQUE COMMENT '用户ID',
    total_earned    BIGINT NOT NULL DEFAULT 0 COMMENT '累计获得积分',
    total_used      BIGINT NOT NULL DEFAULT 0 COMMENT '累计使用积分',
    total_expired   BIGINT NOT NULL DEFAULT 0 COMMENT '累计过期积分',
    available       BIGINT NOT NULL DEFAULT 0 COMMENT '可用积分(冗余)',
    version         INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id)
) ENGINE=InnoDB COMMENT='积分账户';

-- 积分流水(入账)
CREATE TABLE point_record (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_no       VARCHAR(64) NOT NULL UNIQUE COMMENT '流水号',
    user_id         VARCHAR(64) NOT NULL,
    point_amount    BIGINT NOT NULL COMMENT '积分数量(正=入,负=出)',
    type            TINYINT NOT NULL COMMENT '1活动获得 2交易获得 3签到获得 4商城兑换 5过期扣减 6手动调整',
    biz_source      VARCHAR(64) NOT NULL COMMENT '来源业务编码',
    biz_no          VARCHAR(128) COMMENT '来源业务单号',
    expire_time     DATETIME COMMENT '过期时间(入账时写入)',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '1有效 2已过期 3已使用 4已撤回',
    remark          VARCHAR(256),
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_status (user_id, status),
    INDEX idx_expire (expire_time, status),
    INDEX idx_biz (biz_source, biz_no)
) ENGINE=InnoDB COMMENT='积分流水';

-- 积分冻结(兑换预占)
CREATE TABLE point_freeze (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         VARCHAR(64) NOT NULL,
    freeze_amount   BIGINT NOT NULL COMMENT '冻结积分数',
    biz_no          VARCHAR(128) NOT NULL COMMENT '业务单号',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '1冻结中 2已解冻 3已扣减',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at       DATETIME NOT NULL COMMENT '冻结过期时间',
    UNIQUE INDEX uk_biz (biz_no),
    INDEX idx_user (user_id, status)
) ENGINE=InnoDB COMMENT='积分冻结记录';
```

**关键设计点**：
- 积分账户用乐观锁（version）+ 冻结机制，防止超扣
- 积分流水逐笔记录，支持追溯和过期管理
- 过期积分通过定时任务批量处理，避免大表扫描（利用 `idx_expire` 索引）
- 高并发入账通过 RocketMQ 事务消息保证积分不丢不重

---

### 2.3 积分商城 (point-mall-svc)

**核心职责**：商品管理、积分兑换、订单管理、库存控制。

**业务流程**：

```
用户浏览商品 → 查看库存(Redis) → 发起兑换
                                      │
                          ┌───────────┤
                          ▼           ▼
                   积分冻结请求   库存预扣(Redis)
                   (调用point)    (Lua原子操作)
                          │           │
                          └─────┬─────┘
                                ▼
                     创建兑换订单(MySQL)
                                │
                    ┌───────────┤
                    ▼           ▼
              兑换成功       兑换失败
              (扣积分     (解冻积分
               扣库存      回滚库存)
               发放权益)
                    │
                    ▼
              通知rights-svc发货
```

**订单状态机**：

```
待处理(0) → 已兑换(1) → 发货中(2) → 已完成(3)
    │                                      │
    ▼                                      ▼
已取消(4)                               已退款(5)
```

**数据模型**：

```sql
-- 商品
CREATE TABLE mall_product (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_code    VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL COMMENT '商品名称',
    category        TINYINT NOT NULL COMMENT '1实物 2虚拟 3权益类',
    point_price     BIGINT NOT NULL COMMENT '积分价格',
    original_price  DECIMAL(18,2) COMMENT '原价(展示用)',
    total_stock     INT NOT NULL COMMENT '总库存',
    available_stock INT NOT NULL COMMENT '可用库存',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '1上架 2下架',
    display_order   INT NOT NULL DEFAULT 0,
    images          JSON COMMENT '商品图片',
    description     TEXT COMMENT '商品详情',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_order (status, display_order)
) ENGINE=InnoDB COMMENT='商品';

-- 兑换订单
CREATE TABLE mall_order (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no        VARCHAR(64) NOT NULL UNIQUE COMMENT '订单号',
    user_id         VARCHAR(64) NOT NULL,
    product_code    VARCHAR(64) NOT NULL,
    product_name    VARCHAR(128) NOT NULL COMMENT '冗余',
    point_amount    BIGINT NOT NULL COMMENT '消耗积分',
    quantity        INT NOT NULL DEFAULT 1,
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已兑换 2发货中 3已完成 4已取消 5已退款',
    delivery_info   JSON COMMENT '收货信息(实物类)',
    rights_code     VARCHAR(64) COMMENT '权益编码(权益类)',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id, created_at),
    INDEX idx_status (status, created_at)
) ENGINE=InnoDB COMMENT='兑换订单';

-- 库存变更流水
CREATE TABLE mall_stock_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_code    VARCHAR(64) NOT NULL,
    change_amount   INT NOT NULL COMMENT '变化数量(正=入库,负=出库)',
    type            TINYINT NOT NULL COMMENT '1兑换扣减 2退款回滚 3人工调整',
    biz_no          VARCHAR(128) NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_biz (biz_no)
) ENGINE=InnoDB COMMENT='库存流水';
```

**关键设计点**：
- 库存扣减采用 Redis Lua 脚本保证原子性，MySQL 异步对账
- 兑换流程先冻结积分再扣库存，任一失败则回滚，保证不超发
- 订单状态机：待处理→已兑换→发货中→已完成，支持取消和退款

---

### 2.4 权益发放 (rights-svc)

**核心职责**：权益定义、权益库存、权益绑定/发放、供应商对接。

**业务流程**：

```
活动/商城触发权益发放请求
              │
              ▼
      权益库存预扣(Redis+MySQL)
              │
              ▼
      生成权益凭证(code/batch)
              │
       ┌──────┴──────┐
       ▼             ▼
   本地权益       外部供应商权益
   (直接入库)    (调用供应商API)
       │             │
       ┼──────┬──────┘
              ▼
      更新发放状态 → 通知用户(短信/App推送)
              │
              ▼ (失败)
      重试机制(3次) → 标记失败 → 人工处理
```

**数据模型**：

```sql
-- 权益定义
CREATE TABLE rights_definition (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    rights_code     VARCHAR(64) NOT NULL UNIQUE COMMENT '权益编码',
    name            VARCHAR(128) NOT NULL COMMENT '权益名称',
    type            TINYINT NOT NULL COMMENT '1优惠券 2加息券 3VIP服务 4实物 5第三方权益',
    supplier_type   TINYINT NOT NULL COMMENT '0自有 1外部',
    supplier_code   VARCHAR(64) COMMENT '供应商编码',
    total_stock     INT NOT NULL COMMENT '总库存',
    used_stock      INT NOT NULL DEFAULT 0 COMMENT '已用库存',
    valid_days      INT NOT NULL COMMENT '有效天数(发放后)',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 2停用',
    callback_url    VARCHAR(256) COMMENT '供应商回调地址',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='权益定义';

-- 权益凭证(发放实例)
CREATE TABLE rights_instance (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_no     VARCHAR(64) NOT NULL UNIQUE COMMENT '凭证号',
    rights_code     VARCHAR(64) NOT NULL,
    user_id         VARCHAR(64) NOT NULL,
    source_type     TINYINT NOT NULL COMMENT '1活动 2积分兑换 3手动发放',
    source_no       VARCHAR(128) COMMENT '来源单号',
    status          TINYINT NOT NULL DEFAULT 0 COMMENT '0待激活 1已激活 2已使用 3已过期 4已作废',
    activate_time   DATETIME COMMENT '激活时间',
    expire_time     DATETIME COMMENT '过期时间',
    use_time        DATETIME COMMENT '使用时间',
    supplier_order_no VARCHAR(128) COMMENT '供应商订单号',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_status (user_id, status),
    INDEX idx_expire (expire_time, status),
    INDEX idx_source (source_type, source_no)
) ENGINE=InnoDB COMMENT='权益凭证';

-- 权益发放日志
CREATE TABLE rights_issue_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_no     VARCHAR(64) NOT NULL,
    rights_code     VARCHAR(64) NOT NULL,
    user_id         VARCHAR(64) NOT NULL,
    issue_status    TINYINT NOT NULL COMMENT '0发起 1成功 2失败',
    retry_count     INT NOT NULL DEFAULT 0,
    error_msg       VARCHAR(512),
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status_retry (issue_status, retry_count)
) ENGINE=InnoDB COMMENT='权益发放日志';
```

**关键设计点**：
- 权益定义和发放实例分离，定义是模板，实例是实际发放的凭证
- 外部供应商调用采用异步+重试机制，最多3次，失败后转人工
- 权益过期通过定时任务统一处理

---

## 三、跨模块交互流程

### 3.1 活动触发积分发放（核心链路）

```
用户完成活动行为
       │
       ▼
activity-svc: 参与记录落库 → 预算扣减(乐观锁)
       │
       ▼ (RocketMQ 事务消息)
point-svc: 接收积分发放消息
       │
       ├─→ 幂等校验(biz_no唯一索引)
       ├─→ 积分计算(规则引擎)
       ├─→ 积分入账(乐观锁更新point_account)
       └─→ 积分流水落库
              │
              ▼ (RocketMQ 普通消息)
activity-svc: 更新参与记录的reward_status=已发放
```

### 3.2 积分兑换权益商品

```
用户发起兑换
       │
       ▼
point-mall-svc:
  1. Redis Lua 库存预扣
  2. Feign 调用 point-svc 冻结积分
  3. 创建兑换订单
       │
       ▼ (本地消息表 + MQ)
rights-svc:
  1. 权益库存扣减
  2. 生成权益凭证
  3. 调用供应商(如需)
       │
       ▼ (回调 MQ)
point-mall-svc:
  1. 确认积分扣减(冻结→实际扣减)
  2. 更新订单状态为已兑换
  3. 更新MySQL库存(对账)
```

### 3.3 活动直接发放权益

```
activity-svc → MQ → rights-svc
  (活动规则配置权益奖励时)
```

---

## 四、关键技术方案

### 4.1 幂等性保障

| 场景 | 方案 |
|------|------|
| 积分发放 | `point_record.biz_no` 唯一索引，重复消息直接跳过 |
| 库存扣减 | Redis Lua + `mall_stock_log.biz_no` 唯一索引 |
| 权益发放 | `rights_instance.source_no` 唯一索引 |
| 订单创建 | `mall_order.order_no` 唯一索引 |
| 活动参与 | `activity_participation` 用户+活动+时间联合去重 |

### 4.2 高并发应对

| 场景 | 方案 |
|------|------|
| 热门活动参与 | 网关限流(Guava RateLimiter) + Redis 令牌桶 + MQ 削峰 |
| 库存扣减 | Redis 预扣 + Lua 原子操作，MySQL 异步落库对账 |
| 积分查询 | Redis 缓存可用积分，写入时双删 |
| 活动页面 | Redis 缓存活动信息，CDN 静态资源 |

### 4.3 分布式事务

| 场景 | 方案 |
|------|------|
| 活动发积分 | RocketMQ 事务消息（半消息→本地事务→提交/回滚） |
| 积分兑换 | 本地消息表 + 定时补偿（最终一致性） |
| 跨服务组合 | Seata AT 模式（仅在必要时使用，尽量用 MQ 替代） |

### 4.4 数据一致性对账

```
每日凌晨对账任务(XXL-Job):
  1. 积分账户余额 = ∑积分流水(有效入账) - ∑积分流水(有效出账)
  2. Redis库存 与 MySQL库存 一致性校验
  3. 权益发放状态 与 供应商状态 比对
  4. 活动预算 = ∑预算流水
  差异记录 → 对账差异表 → 告警 + 人工处理
```

### 4.5 接口幂等通用方案

```java
// 基于 Redis + 唯一业务号的幂等控制
// 1. 请求到达时，以 bizNo 为 key 写入 Redis（SETNX）
// 2. 写入成功则处理业务，写入失败则查询已有结果直接返回
// 3. 业务处理完成，更新 Redis 为处理结果（带 TTL）
```

---

## 五、部署架构

```
                    ┌───────────────────┐
                    │    K8s Ingress     │
                    └─────────┬─────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
        ┌──────────┐   ┌──────────┐   ┌──────────┐
        │gateway   │   │gateway   │   │gateway   │
        │  pod×2   │   │  pod×2   │   │  pod×2   │
        └────┬─────┘   └────┬─────┘   └────┬─────┘
             │              │              │
    ┌────────┼──────────────┼──────────────┤
    ▼        ▼              ▼              ▼
activity  point      point-mall     rights
 pod×3    pod×3       pod×3         pod×3
    │        │              │              │
    └────────┴──────────────┴──────────────┘
                      │
         ┌────────────┼────────────┐
         ▼            ▼            ▼
    MySQL主从    Redis Cluster   RocketMQ
     (RDS)      (6节点3主3从)    (3Broker)
```

---

## 六、接口设计规范

### 6.1 RESTful 规范

```
POST   /api/v1/{module}           # 创建
GET    /api/v1/{module}/{id}      # 查询详情
GET    /api/v1/{module}           # 分页查询
PUT    /api/v1/{module}/{id}      # 更新
DELETE /api/v1/{module}/{id}      # 删除(逻辑)
```

### 6.2 接口示例

```
POST   /api/v1/activities                         # 创建活动
PUT    /api/v1/activities/{id}/audit               # 审批活动
POST   /api/v1/activities/{code}/participate       # 参与活动
GET    /api/v1/point/accounts/{userId}             # 查询积分账户
POST   /api/v1/point/records/issue                 # 积分发放(内部)
POST   /api/v1/mall/orders                         # 创建兑换订单
POST   /api/v1/rights/instances/issue              # 权益发放(内部)
```

### 6.3 统一响应体

```json
{
  "code": "200",
  "message": "success",
  "data": {},
  "traceId": "xxx"
}
```

### 6.4 错误码规范

| 错误码 | 含义 |
|--------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 10001 | 活动不存在 |
| 10002 | 活动已结束 |
| 10003 | 预算不足 |
| 20001 | 积分余额不足 |
| 20002 | 积分冻结失败 |
| 30001 | 库存不足 |
| 30002 | 订单不存在 |
| 40001 | 权益库存不足 |
| 40002 | 权益发放失败 |

---

## 七、开发落地顺序

| 阶段 | 内容 |
|------|------|
| **P0 - 基础骨架** | 各服务 Maven 多模块搭建、Nacos 配置中心、Gateway 网关、统一异常/响应/日志 |
| **P1 - 积分系统** | 积分账户、积分流水、积分发放/扣减（其他模块依赖积分，优先建设） |
| **P2 - 活动系统** | 活动 CRUD + 审批流 + 参与记录 + 积分发放联调 |
| **P3 - 权益发放** | 权益定义 + 凭证生成 + 供应商对接框架 + 过期处理 |
| **P4 - 积分商城** | 商品管理 + 库存控制 + 兑换流程（依赖积分+权益） |
| **P5 - 集成联调** | 全链路贯通、对账、压测、安全审计 |
