# P4 积分商城 (point-mall-svc) — 完整实现方案

## 1. 模块概述

point-mall-svc 是银行营销平台四个微服务中最后一个实现的模块（P4），负责积分商城的商品管理、库存控制、兑换流程和订单管理。该模块依赖已实现的 **point-svc**（积分冻结/扣减）和 **rights-svc**（权益发放）。

### 当前状态

- 完整 DDD 骨架已存在（22 个文件）：Entity、DTO、Enum、Mapper、Controller、FeignClient
- 但所有 Service 方法均为 `UnsupportedOperationException("Not implemented yet")` 占位
- 无 SQL 建表脚本，无 Redisson/DistributedLockHelper
- 无 point-api、rights-api 依赖

### 已有资产

| 层级 | 文件 | 状态 |
|------|------|------|
| **Entity** | `MallProduct`（10 字段）、`MallOrder`（11 字段，已有 `rightsCode`）、`MallStockLog`（6 字段） | 需增补字段 |
| **Enum** | `OrderStatus`（6 态）、`ProductCategory`（3 类：PHYSICAL/VIRTUAL/RIGHTS） | 无需修改 |
| **DTO** | `OrderCreateRequest`、`OrderResponse`、`ProductCreateRequest`、`ProductResponse` | 需增补字段 + 新建 `ProductUpdateRequest` |
| **Mapper** | 3 个 Mapper，均继承 `BaseMapper`，无自定义方法 | 无需修改 |
| **Controller** | `ProductController`（5 端点）、`OrderController`（4 端点） | 参数类型微调 |
| **ErrorCode** | `MALL_STOCK_INSUFFICIENT(30001)`、`MALL_ORDER_NOT_FOUND(30002)`、`MALL_PRODUCT_NOT_FOUND(30003)`、`MALL_PRODUCT_OFF_SHELF(30004)` | 需新增 2 个 |

### 技术栈依赖

- Spring Boot 3.2.5，Spring Cloud 2023.0.1，MyBatis-Plus 3.5.6
- Nacos 服务发现，服务名 `point-mall`，端口 8083
- MySQL 数据库 `wuduo_bank_mall`（已创建，但表不存在）
- Redisson 3.27.2 分布式锁
- 跨服务调用：`PointFeignClient`（冻结→扣减）、`RightsFeignClient`（发放权益）

---

## 2. 核心设计

### 2.1 兑换流程（同步补偿模式）

原始技术方案使用 RocketMQ 做异步履约，但 docker-compose 无 MQ 基础设施。P4 采用 **同步 Feign + 补偿回滚** 方案：

```
用户发起兑换
    │
    ▼
① 校验商品（存在 + 上架）
    │
    ▼
② 原子扣库存（MySQL UPDATE SET available_stock = available_stock - N WHERE available_stock >= N）
    │
    ▼
③ 生成订单号 → 创建订单 status=PENDING
    │
    ▼
④ 冻结积分（Feign → point-svc.freeze）
    │ 失败 → 回滚库存 + 取消订单
    ▼
⑤ 扣减积分（Feign → point-svc.deduct with freezeNo）
    │ 失败 → 解冻积分 + 回滚库存 + 取消订单
    ▼
⑥ 写库存日志（MallStockLog）
    │
    ▼
⑦ 发放权益（如果 category=RIGHTS）(Feign → rights-svc.issue)
    │ 失败 → 记录日志，不阻塞（积分已扣，人工处理）
    ▼
⑧ 更新订单 status=EXCHANGED
```

### 2.2 订单状态机

```
PENDING(0) ──→ EXCHANGED(1) ──→ DELIVERING(2) ──→ COMPLETED(3)
    │                                                    │
    ▼                                                    ▼
CANCELLED(4)                                      REFUNDED(5)
```

| 状态 | Code | 说明 | 可迁移到 |
|------|------|------|----------|
| PENDING | 0 | 待处理，已创建订单 | EXCHANGED, CANCELLED |
| EXCHANGED | 1 | 已兑换，积分已扣 | DELIVERING, REFUNDED |
| DELIVERING | 2 | 配送中 | COMPLETED |
| COMPLETED | 3 | 已完成 | REFUNDED |
| CANCELLED | 4 | 已取消（从 PENDING） | 终态 |
| REFUNDED | 5 | 已退款（从 EXCHANGED/COMPLETED） | 终态 |

**本期实现的状态流转：**
- `PENDING → EXCHANGED`：兑换成功
- `PENDING → CANCELLED`：用户取消（回滚库存 + 解冻积分）
- DELIVERING/COMPLETED/REFUNDED 预留后续扩展

### 2.3 编号生成规则

| 编号类型 | 格式 | 示例 |
|----------|------|------|
| 商品编码 productCode | `MALL` + yyyyMMdd + 4位随机数 | `MALL202607174385` |
| 订单号 orderNo | `MO` + yyyyMMddHHmmss + 6位随机数 | `MO20260717143000123456` |

### 2.4 跨服务调用关系

```
point-mall-svc
    │
    ├── Feign → point-svc (name="point")
    │   ├── freeze(userId, freezeAmount, bizNo)    → PointFreezeResponse
    │   ├── deduct(userId, pointAmount, type=4, bizNo, freezeNo) → PointAccountResponse
    │   └── unfreeze(userId, bizNo)                → PointFreezeResponse（补偿用）
    │
    └── Feign → rights-svc (name="rights")
        └── issue(rightsCode, userId, sourceType=2, sourceNo) → RightsIssueResponse
```

**幂等性保障：**

| 操作 | 幂等键 | 唯一索引 |
|------|--------|----------|
| 积分冻结 | `bizNo = "FZ" + orderNo` | `point_freeze.uk_biz_no` |
| 积分扣减 | `bizNo = orderNo` | `point_record.biz_no` 逻辑校验 |
| 权益发放 | `sourceNo = orderNo` | `rights_instance.uk_source_no` |
| 创建订单 | `orderNo` | `mall_order.uk_order_no` |

**类型转换注意：**
- `RightsIssueRequest.userId` 为 `String` 类型，mall 的 userId 为 `Long`，需 `String.valueOf(userId)`
- `PointFreezeRequest.userId` / `PointDeductRequest.userId` 为 `Long`，与 mall 一致

### 2.5 Feign 错误处理

point-svc 的 `GlobalExceptionHandler` 对 `BizException` 返回 **HTTP 200** + `R.fail()`，Feign 不会抛异常。但 `R.fail()` 的 JSON（`code/message/data/traceId`）与正常响应 DTO 字段不匹配 → 反序列化后所有字段为 null。

**应对：** 调用 Feign 后检查关键字段是否为 null：
```java
PointFreezeResponse freezeResp = pointFeignClient.freeze(freezeReq);
if (freezeResp == null || freezeResp.getFreezeNo() == null) {
    // 积分服务返回业务错误
    throw new BizException(ErrorCode.MALL_POINT_OPERATION_FAILED);
}
```

---

## 3. 改动范围

### 3.1 新建文件（6 个）

| # | 文件 | 说明 |
|---|------|------|
| 1 | `docker/mysql/init/05-init-mall-tables.sql` | 3 张表 DDL（幂等 `IF NOT EXISTS`） |
| 2 | `mall-api/.../dto/ProductUpdateRequest.java` | 部分更新 DTO（所有字段可选） |
| 3 | `mall-infrastructure/.../config/RedissonConfig.java` | 复制 point 模块，单机 Redis |
| 4 | `mall-infrastructure/.../util/DistributedLockHelper.java` | 复制 point 模块，lock prefix 改为 `mall:lock:` |
| 5 | `mall-boot/.../config/RestTemplateConfig.java` | `@LoadBalanced RestTemplate` bean |
| 6 | `point-mall/Points-Mall-Plan.md` | 本方案文档 |

### 3.2 修改文件（14 个）

| # | 文件 | 操作 | 说明 |
|---|------|------|------|
| 1 | `mall-domain/.../entity/MallProduct.java` | 增补字段 | 新增 `rightsCode`（String，可为空） |
| 2 | `mall-domain/.../entity/MallOrder.java` | 增补字段 | 新增 `rightsInstanceNo`（String，可为空） |
| 3 | `mall-api/.../dto/OrderCreateRequest.java` | 增补字段 | 新增 `@NotNull Long userId` |
| 4 | `mall-api/.../dto/OrderResponse.java` | 增补字段 | 新增 `rightsInstanceNo`、`category` |
| 5 | `mall-api/.../dto/ProductResponse.java` | 增补字段 | 新增 `rightsCode` |
| 6 | `common/.../model/ErrorCode.java` | 新增枚举 | `MALL_ORDER_STATUS_INVALID(30005)`、`MALL_POINT_OPERATION_FAILED(30006)` |
| 7 | `mall-application/.../service/ProductService.java` | 改签名 | `update(id, ProductUpdateRequest)`、`page(..., category)` |
| 8 | `mall-application/.../service/OrderService.java` | 改签名 | `page(userId, pageNum, pageSize)` |
| 9 | `mall-application/.../service/impl/ProductServiceImpl.java` | **重写** | 完整商品 CRUD + 上下架 |
| 10 | `mall-application/.../service/impl/OrderServiceImpl.java` | **重写** | 完整兑换流程 + 取消 + 补偿 |
| 11 | `mall-application/pom.xml` | 加依赖 | `point-api`、`rights-api` |
| 12 | `mall-infrastructure/pom.xml` | 加依赖 | `redisson-spring-boot-starter` |
| 13 | `mall-boot/.../controller/ProductController.java` | 改参数 | update → ProductUpdateRequest，page → category |
| 14 | `mall-boot/.../controller/OrderController.java` | 改参数 | page → userId |
| 15 | `mall-boot/.../BootApplication.java` | 改注解 | `@EnableScheduling`、`scanBasePackages`、`@EnableFeignClients` |

---

## 4. SQL DDL

```sql
-- 数据库 wuduo_bank_mall 已由 01-init-databases.sql 创建

CREATE TABLE IF NOT EXISTS mall_product (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_code    VARCHAR(64) NOT NULL,
    name            VARCHAR(128) NOT NULL COMMENT '商品名称',
    category        TINYINT NOT NULL COMMENT '1实物 2虚拟 3权益',
    point_price     BIGINT NOT NULL COMMENT '积分价格',
    original_price  DECIMAL(10,2) COMMENT '原价',
    total_stock     INT NOT NULL COMMENT '总库存',
    available_stock INT NOT NULL COMMENT '可用库存',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
    display_order   INT NOT NULL DEFAULT 0,
    images          VARCHAR(512) COMMENT '图片JSON',
    description     VARCHAR(1024),
    rights_code     VARCHAR(64) COMMENT '关联权益编码(RIGHTS类商品)',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT NOT NULL DEFAULT 0,
    UNIQUE INDEX uk_product_code (product_code),
    INDEX idx_category_status (category, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城商品';

CREATE TABLE IF NOT EXISTS mall_order (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no            VARCHAR(64) NOT NULL,
    user_id             BIGINT NOT NULL COMMENT '用户ID',
    product_code        VARCHAR(64) NOT NULL,
    product_name        VARCHAR(128) NOT NULL COMMENT '商品名称(快照)',
    point_amount        BIGINT NOT NULL COMMENT '兑换积分',
    quantity            INT NOT NULL COMMENT '兑换数量',
    status              TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已兑换 2配送中 3已完成 4已取消 5已退款',
    delivery_info       VARCHAR(256) COMMENT '配送信息',
    rights_code         VARCHAR(64) COMMENT '关联权益编码',
    rights_instance_no  VARCHAR(64) COMMENT '权益实例号',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT NOT NULL DEFAULT 0,
    UNIQUE INDEX uk_order_no (order_no),
    INDEX idx_user_created (user_id, created_at),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城订单';

CREATE TABLE IF NOT EXISTS mall_stock_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_code    VARCHAR(64) NOT NULL,
    change_type     TINYINT NOT NULL COMMENT '1兑换扣减 2取消恢复 3手动调整',
    change_quantity INT NOT NULL COMMENT '变动数量(正增负减)',
    before_stock    INT NOT NULL COMMENT '变动前库存',
    after_stock     INT NOT NULL COMMENT '变动后库存',
    order_no        VARCHAR(64) COMMENT '关联订单号',
    remark          VARCHAR(256),
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT NOT NULL DEFAULT 0,
    INDEX idx_product (product_code),
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城库存日志';
```

---

## 5. 服务实现详述

### 5.1 ProductServiceImpl

**注入依赖：** `MallProductMapper`

#### `create(ProductCreateRequest)` → ProductResponse

1. 校验 `totalStock > 0`、`pointPrice > 0`
2. 生成 productCode：`"MALL" + LocalDate.now().format(yyyyMMdd) + 4位随机数`
3. 构建 MallProduct：`availableStock = totalStock`，`status = 1`（上架），`displayOrder = 0`
4. Insert，返回 toResponse

#### `update(Long id, ProductUpdateRequest)` → ProductResponse

1. 查已有商品，不存在 → `MALL_PRODUCT_NOT_FOUND(30003)`
2. 合并非 null 字段到 entity（name, category, pointPrice, originalPrice, totalStock, images, description, displayOrder, rightsCode）
3. 若 totalStock 变更：校验 `newTotalStock >= (旧totalStock - 旧availableStock)`（已使用库存不可回收）
4. UpdateById，返回 toResponse

#### `getById(Long id)` → ProductResponse

1. 查商品，不存在 → `MALL_PRODUCT_NOT_FOUND(30003)`
2. 返回 toResponse

#### `page(pageNum, pageSize, category)` → List\<ProductResponse\>

1. `LambdaQueryWrapper<MallProduct>`，可选 `.eq(category)`
2. 按 `displayOrder DESC, id DESC` 排序
3. MyBatis-Plus `Page`，返回映射列表

#### `onOffShelf(Long id, Integer status)` → ProductResponse

1. 查商品，校验 status 为 0 或 1
2. 更新 status 字段，返回 toResponse

---

### 5.2 OrderServiceImpl（核心）

**注入依赖：** `MallProductMapper`、`MallOrderMapper`、`MallStockLogMapper`、`DistributedLockHelper`、`PointFeignClient`、`RightsFeignClient`

#### `create(OrderCreateRequest)` → OrderResponse

**分布式锁：** `mall:lock:{userId}`（wait 3s, lease 10s）

```
1. 校验入参: quantity > 0, userId 非空
2. 查商品:
   - product = productMapper.selectOne(eq(productCode))
   - null → MALL_PRODUCT_NOT_FOUND
   - status != 1 → MALL_PRODUCT_OFF_SHELF
3. 生成订单号: "MO" + yyyyMMddHHmmss + 6位随机数
4. 计算积分: totalPoints = product.pointPrice × quantity
5. 创建订单: status=PENDING(0), insert
6. 原子扣库存:
   mapper.update(null, LambdaUpdateWrapper<MallProduct>()
       .eq(id, product.getId())
       .setSql("available_stock = available_stock - " + quantity)
       .ge(MallProduct::getAvailableStock, quantity))
   → 影响行数=0: 抛 MALL_STOCK_INSUFFICIENT
7. 冻结积分 (Feign → point-svc):
   PointFreezeRequest: userId, freezeAmount=totalPoints, bizNo="FZ"+orderNo
   → freezeResp.freezeNo == null: rollbackStock → throw
8. 写库存日志: changeType=1, changeQuantity=-quantity, orderNo
9. 扣减积分 (Feign → point-svc):
   PointDeductRequest: userId, pointAmount=totalPoints, type=4(MALL_EXCHANGE),
                       bizNo=orderNo, freezeNo=step7.freezeNo
   → 失败: unfreezePoints + rollbackStock → throw
10. 发放权益 (仅 category=RIGHTS 且 rightsCode 非空):
    RightsIssueRequest: rightsCode, userId=String.valueOf(userId),
                        sourceType=2(MALL_EXCHANGE), sourceNo=orderNo
    → 成功: order.rightsInstanceNo = resp.getInstanceNo()
    → 失败: 不抛异常, 记录日志 (积分已扣, 人工处理)
11. 更新订单: status=EXCHANGED(1), rightsInstanceNo
12. 返回 OrderResponse
```

**私有补偿方法：**

```java
// 回滚库存
private void rollbackStock(Long productId, int quantity) {
    productMapper.update(null, new LambdaUpdateWrapper<MallProduct>()
        .eq(MallProduct::getId, productId)
        .setSql("available_stock = available_stock + " + quantity));
}

// 解冻积分
private void unfreezePoints(Long userId, String freezeBizNo) {
    try {
        PointFreezeRequest req = new PointFreezeRequest();
        req.setUserId(userId);
        req.setBizNo(freezeBizNo);
        pointFeignClient.unfreeze(req);
    } catch (Exception e) { log.error("unfreeze failed", e); }
}
```

#### `getById(Long id)` → OrderResponse

1. 查订单，不存在 → `MALL_ORDER_NOT_FOUND(30002)`
2. 返回 toResponse

#### `page(Long userId, Integer pageNum, Integer pageSize)` → List\<OrderResponse\>

1. `LambdaQueryWrapper<MallOrder>.eq(userId)`
2. 按 `created_at DESC` 排序
3. MyBatis-Plus `Page`，返回映射列表

#### `cancel(Long id)` → OrderResponse

1. 查订单
2. 校验 status = PENDING(0) → 否则 `MALL_ORDER_STATUS_INVALID(30005)`
3. 回滚库存：`available_stock += order.quantity`
4. 解冻积分：`pointFeignClient.unfreeze(userId, "FZ" + order.orderNo)`
5. 更新 status = CANCELLED(4)
6. 写库存日志：changeType=2, changeQuantity=+quantity
7. 返回 response

---

## 6. API 接口

```
# 商品管理
POST   /api/v1/mall/products                 创建商品
PUT    /api/v1/mall/products/{id}            更新商品（部分字段）
GET    /api/v1/mall/products/{id}            查询商品详情
GET    /api/v1/mall/products?pageNum=&pageSize=&category=  分页查询（category 可选）
PUT    /api/v1/mall/products/{id}/shelf?status=            上下架（0/1）

# 订单管理
POST   /api/v1/mall/orders                   创建兑换订单
GET    /api/v1/mall/orders/{id}              查询订单详情
GET    /api/v1/mall/orders?userId=&pageNum=&pageSize=      分页查询用户订单
PUT    /api/v1/mall/orders/{id}/cancel       取消订单（仅 PENDING 状态）
```

---

## 7. 关键设计决策

| 决策 | 原因 |
|------|------|
| **同步 Feign 代替 MQ** | 项目无 RocketMQ，与 P3 务实风格一致 |
| **MySQL 原子库存** | 使用 `setSql(...).ge(...)` 而非 Redis Lua，与 rights 模块模式统一 |
| **权益发放失败不阻塞** | 积分已扣后权益失败不抛异常，订单仍标记 EXCHANGED，人工补偿 |
| **取消仅限 PENDING** | 简化实现；完整退款流程需后续扩展 |
| **分布式锁按 userId** | 防止同一用户并发兑换导致积分/库存不一致 |
| **时间命名：createdAt/updatedAt** | mall 模块使用 `createdAt`/`updatedAt`，不可与 rights 模块的 `createTime`/`updateTime` 混淆 |

---

## 8. 实施顺序

### Phase 1: 基础设施（无业务逻辑）
1. 创建 `05-init-mall-tables.sql`
2. 创建 `RedissonConfig.java`
3. 创建 `DistributedLockHelper.java`
4. 创建 `RestTemplateConfig.java`
5. 更新 `mall-infrastructure/pom.xml`（加 Redisson）

### Phase 2: API/Domain 层
6. 新建 `ProductUpdateRequest.java`
7. 增补 `OrderCreateRequest.userId`
8. 增补 `OrderResponse`、`ProductResponse` 字段
9. 增补 `MallProduct.rightsCode`、`MallOrder.rightsInstanceNo`
10. 新增 `ErrorCode` 枚举值

### Phase 3: Service 层
11. 更新 `ProductService`、`OrderService` 接口签名
12. 更新 `mall-application/pom.xml`（加 point-api, rights-api）
13. 实现 `ProductServiceImpl`
14. 实现 `OrderServiceImpl`（核心）

### Phase 4: Controller + Boot
15. 更新 `ProductController`、`OrderController`
16. 更新 `BootApplication`

---

## 9. 验证计划

### 编译
```bash
mvn clean compile -pl point-mall -am -DskipTests
```

### 构建
```bash
mvn clean install -f common/pom.xml -DskipTests
mvn clean install -f point-mall/pom.xml -DskipTests
```

### API 测试场景

| # | 测试场景 | 预期结果 |
|---|---------|----------|
| 1 | 创建商品（3 种 category） | 返回 productCode，status=1 |
| 2 | 分页查询商品（按 category 筛选） | 返回筛选后列表 |
| 3 | 更新商品（改 price/stock） | 字段更新成功 |
| 4 | 商品上架/下架 | status 0↔1 |
| 5 | 给用户发放积分（point-svc issue） | 积分到账 |
| 6 | 创建兑换订单（完整流程） | 库存扣减 → 积分冻结→扣减 → 订单=EXCHANGED |
| 7 | 兑换 RIGHTS 类商品 | 权益发放成功，order.rightsInstanceNo 非空 |
| 8 | 库存不足 | `MALL_STOCK_INSUFFICIENT (30001)` |
| 9 | 商品已下架 | `MALL_PRODUCT_OFF_SHELF (30004)` |
| 10 | 积分不足 | `MALL_POINT_OPERATION_FAILED (30006)`（point-svc 传递） |
| 11 | 取消订单（PENDING 状态） | 库存恢复 + 积分解冻 + status=CANCELLED |
| 12 | 取消已兑换订单 | `MALL_ORDER_STATUS_INVALID (30005)` |
| 13 | 查询用户订单列表 | 按 userId 分页返回 |