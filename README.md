# **银行企业金融App营销系统**



四个核心模块（活动系统、积分系统、积分商城、权益发放）

这套方案适用于**高并发、高可用、数据强一致性**的银行级系统。













## 使用说明

● 全部配置已对齐。下面是完整的使用说明。

---
  Docker Compose 使用方式

  1. 前提条件

  确保本机已安装 Docker Desktop 并启动。验证：

  docker --version
  docker compose version

  2. 启动中间件

  在项目根目录执行：

  cd D:\wuduo\code\wuduo-bank-marketing
  docker compose up -d

  首次运行会拉取镜像，需要等几分钟。启动后检查状态：

  docker compose ps

  预期输出三个服务都是 running / healthy：
  ┌─────────────┬──────┬───────────────┐
  │    容器     │ 端口 │     用途      │
  ├─────────────┼──────┼───────────────┤
  │ wuduo-mysql │ 3306 │ 数据库        │
  ├─────────────┼──────┼───────────────┤
  │ wuduo-redis │ 6379 │ 缓存          │
  ├─────────────┼──────┼───────────────┤
  │ wuduo-nacos │ 8848 │ 注册/配置中心 │
  └─────────────┴──────┴───────────────┘
  3. 验证中间件

  # MySQL - 验证库已创建
  docker exec wuduo-mysql mysql -uroot -pwuduo123 -e "SHOW DATABASES;"

  # Redis - 验证可连接
  docker exec wuduo-redis redis-cli -a wuduo123 ping

  # Nacos - 浏览器访问控制台
  # http://localhost:8848/nacos  账号: nacos / nacos

  4. 启动 Java 服务

  中间件就绪后，用 IDEA 启动各服务的 BootApplication：
  ┌───────────────────────────────────────────┬──────┬───────────────────────────┐
  │                  启动类                   │ 端口 │         启动顺序          │
  ├───────────────────────────────────────────┼──────┼───────────────────────────┤
  │ com.wuduo.bank.activity.BootApplication   │ 8081 │ 可并行                    │
  ├───────────────────────────────────────────┼──────┼───────────────────────────┤
  │ com.wuduo.bank.point.BootApplication      │ 8082 │ 可并行                    │
  ├───────────────────────────────────────────┼──────┼───────────────────────────┤
  │ com.wuduo.bank.mall.BootApplication       │ 8083 │ 可并行                    │
  ├───────────────────────────────────────────┼──────┼───────────────────────────┤
  │ com.wuduo.bank.rights.BootApplication     │ 8084 │ 可并行                    │
  ├───────────────────────────────────────────┼──────┼───────────────────────────┤
  │ com.wuduo.bank.gateway.GatewayApplication │ 8080 │ 等上面注册到Nacos后再启动 │
  └───────────────────────────────────────────┴──────┴───────────────────────────┘
  启动后访问 Knife4j 文档验证：

  - http://localhost:8081/doc.html (活动)
  - http://localhost:8082/doc.html (积分)
  - http://localhost:8083/doc.html (商城)
  - http://localhost:8084/doc.html (权益)

  5. 通过网关访问

  所有请求走网关 8080 端口：

  GET  http://localhost:8080/api/v1/activities
  GET  http://localhost:8080/api/v1/point/accounts/{userId}
  GET  http://localhost:8080/api/v1/mall/products
  GET  http://localhost:8080/api/v1/rights/definitions

  6. 常用命令

  # 停止所有容器
  docker compose down

  # 停止并清除数据卷(重置所有数据)
  docker compose down -v

  # 查看日志
  docker compose logs -f mysql
  docker compose logs -f nacos

  # 单独重启某个服务
  docker compose restart nacos

  7. 配置对应关系

  应用默认配置和 Docker 中间件已自动对齐，无需额外修改：
  ┌────────────┬──────────────────┬─────────────────────┐
  │   配置项   │  Docker 中间件   │     应用默认值      │
  ├────────────┼──────────────────┼─────────────────────┤
  │ MySQL 地址 │ wuduo-mysql:3306 │ 127.0.0.1:3306      │
  ├────────────┼──────────────────┼─────────────────────┤
  │ MySQL 密码 │ wuduo123         │ wuduo123            │
  ├────────────┼──────────────────┼─────────────────────┤
  │ 活动库     │ 自动创建         │ wuduo_bank_activity │
  ├────────────┼──────────────────┼─────────────────────┤
  │ 积分库     │ 自动创建         │ wuduo_bank_point    │
  ├────────────┼──────────────────┼─────────────────────┤
  │ 商城库     │ 自动创建         │ wuduo_bank_mall     │
  ├────────────┼──────────────────┼─────────────────────┤
  │ 权益库     │ 自动创建         │ wuduo_bank_rights   │
  ├────────────┼──────────────────┼─────────────────────┤
  │ Redis 地址 │ wuduo-redis:6379 │ 127.0.0.1:6379      │
  ├────────────┼──────────────────┼─────────────────────┤
  │ Redis 密码 │ wuduo123         │ wuduo123            │
  ├────────────┼──────────────────┼─────────────────────┤
  │ Nacos 地址 │ wuduo-nacos:8848 │ 127.0.0.1:8848      │
  └────────────┴──────────────────┴─────────────────────┘
  如需修改密码，只需改 docker-compose.yml 里的环境变量，启动服务时传入对应的 -DMYSQL_PASSWORD=xxx 即可。