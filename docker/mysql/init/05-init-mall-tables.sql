-- ===================================================
-- P4 积分商城表结构初始化
-- Database: wuduo_bank_mall (created by 01-init-databases.sql)
-- ===================================================

USE wuduo_bank_mall;

CREATE TABLE IF NOT EXISTS mall_product (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_code    VARCHAR(64) NOT NULL COMMENT '商品编码',
    name            VARCHAR(128) NOT NULL COMMENT '商品名称',
    category        TINYINT NOT NULL COMMENT '1实物 2虚拟 3权益',
    point_price     BIGINT NOT NULL COMMENT '积分价格',
    original_price  DECIMAL(10,2) COMMENT '原价',
    total_stock     INT NOT NULL COMMENT '总库存',
    available_stock INT NOT NULL COMMENT '可用库存',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
    display_order   INT NOT NULL DEFAULT 0 COMMENT '展示排序',
    images          VARCHAR(512) COMMENT '图片JSON数组',
    description     VARCHAR(1024) COMMENT '商品描述',
    rights_code     VARCHAR(64) COMMENT '关联权益编码(RIGHTS类商品)',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
    UNIQUE INDEX uk_product_code (product_code),
    INDEX idx_category_status (category, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城商品';

CREATE TABLE IF NOT EXISTS mall_order (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no            VARCHAR(64) NOT NULL COMMENT '订单号',
    user_id             BIGINT NOT NULL COMMENT '用户ID',
    product_code        VARCHAR(64) NOT NULL COMMENT '商品编码',
    product_name        VARCHAR(128) NOT NULL COMMENT '商品名称(快照)',
    point_amount        BIGINT NOT NULL COMMENT '兑换积分',
    quantity            INT NOT NULL COMMENT '兑换数量',
    status              TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已兑换 2配送中 3已完成 4已取消 5已退款',
    delivery_info       VARCHAR(256) COMMENT '配送信息',
    rights_code         VARCHAR(64) COMMENT '关联权益编码',
    rights_instance_no  VARCHAR(64) COMMENT '权益实例号',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
    UNIQUE INDEX uk_order_no (order_no),
    INDEX idx_user_created (user_id, created_at),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城订单';

CREATE TABLE IF NOT EXISTS mall_stock_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_code    VARCHAR(64) NOT NULL COMMENT '商品编码',
    change_type     TINYINT NOT NULL COMMENT '1兑换扣减 2取消恢复 3手动调整',
    change_quantity INT NOT NULL COMMENT '变动数量(正增负减)',
    before_stock    INT NOT NULL COMMENT '变动前库存',
    after_stock     INT NOT NULL COMMENT '变动后库存',
    order_no        VARCHAR(64) COMMENT '关联订单号',
    remark          VARCHAR(256) COMMENT '备注',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted         TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_product (product_code),
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商城库存日志';
