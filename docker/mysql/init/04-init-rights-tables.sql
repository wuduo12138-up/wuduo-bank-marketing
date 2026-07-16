-- ============================================================
-- Rights (权益) 模块数据库初始化
-- ============================================================

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
    INDEX idx_status_retry (operation_result, retry_count),
    INDEX idx_instance (instance_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权益发放日志';
