# RequestRecorder

Spring Boot 2.x / 3.x 通用的 HTTP 请求采集 Starter：引入一个依赖 + 一段 yml 配置，自动采集请求与响应并写入**独立的采集数据库**，供 Python Tkinter 回放客户端做请求回放。

版本：`1.0.0`

## 模块

| 模块 | 说明 |
| --- | --- |
| `request-recorder-core` | 纯 Java 核心：配置、模型、异步队列、独立连接池、脱敏、仓储、采集判定（无 Spring / Servlet 依赖） |
| `request-recorder-spring-boot-starter` | 版本无关公共自动装配（属性绑定、装配、生命周期） |
| `request-recorder-spring-boot-2-starter` | Boot 2 适配（`javax.servlet` + `spring.factories`） |
| `request-recorder-spring-boot-3-starter` | Boot 3 适配（`jakarta.servlet` + `AutoConfiguration.imports`） |
| `request-recorder-integration-tests-boot2` | Boot 2 端到端测试（不发布） |
| `request-recorder-integration-tests-boot3` | Boot 3 端到端测试（不发布） |

## 快速开始

1. 建库建表：`mysql -uroot -p < ../sql/request_recorder.sql`
2. 引入依赖（二选一）：

```xml
<!-- Spring Boot 2.x -->
<dependency>
    <groupId>com.requestrecorder</groupId>
    <artifactId>request-recorder-spring-boot-2-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Spring Boot 3.x -->
<dependency>
    <groupId>com.requestrecorder</groupId>
    <artifactId>request-recorder-spring-boot-3-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

3. 配置 `application.yml`：

```yaml
request-recorder:
  project-code: my-service
  database:
    jdbc-url: jdbc:mysql://127.0.0.1:3306/request_recorder?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: recorder
    password: change-me
```

无需任何采集代码，启动即生效。

## 文档

| 文档 | 内容 |
| --- | --- |
| [docs/使用说明.md](docs/使用说明.md) | 接入步骤、最小示例、多项目共用、与 Python GUI 配合、目录结构、采集机制、常见问题 |
| [docs/配置说明.md](docs/配置说明.md) | 全量配置项（含默认值、必填性）、校验规则、写法注意事项 |
| [docs/发布说明.md](docs/发布说明.md) | 构建安装、私服发布、产物核对、版本管理、发布检查单 |
| [docs/兼容性说明.md](docs/兼容性说明.md) | 版本矩阵、Boot 2/3 差异、与既有组件和 GUI 的兼容性、数据表字段、已知限制 |

## 构建

```bash
mvn clean install
```

Boot 3 模块需 JDK 17 编译；core 与 Boot 2 模块字节码为 Java 8。

## 校验工具

| 工具 | 用途 |
| --- | --- |
| `tools/verify_gui_compat.py` | 只读校验采集库与 Python GUI 的字段/查询兼容性（读 `%APPDATA%/RequestReplay/config.json`） |
| `MySqlSmokeTest`（boot3 集成测试） | 真实 MySQL 8.0 落库冒烟测试，默认跳过，需显式开启 |

```bash
# 1. GUI 兼容性校验（只读，可重复执行）
cd tools
python verify_gui_compat.py --repository ../request-recorder-core/src/main/java/com/requestrecorder/core/database/RecorderRepository.java

# 2. 真实 MySQL 落库冒烟（连接参数用环境变量，避免命令行转义）
export RR_IT_MYSQL_USER=root RR_IT_MYSQL_PASSWORD=你的密码 RR_IT_MYSQL_DATABASE=request_recorder
mvn -pl request-recorder-integration-tests-boot3 test -Drequest-recorder.it.mysql=true
```

冒烟测试使用一次性 `project-code`，结束前只清理自身写入的数据，不影响库内既有记录。

## 设计要点

- **独立数据源**：采集库连接池由组件自建，不与业务 `DataSource` / 事务 / MyBatis 共享；未引入 HikariCP 时自动回退 JDBC 直连。
- **异步不阻塞**：有界队列 + 可配置 worker，队列满丢弃并计数，业务线程不等待落库。
- **防回放死循环**：回放请求带 `X-Request-Replay: true` 时不采集。
- **默认脱敏**：`Authorization` / `Cookie` / `Set-Cookie` / `X-Api-Key` 等头与 `password` / `token` / `id_card` / `mobile` 等字段统一替换为 `******`。
- **故障隔离**：配置非法或采集库不可用时只告警与计数，绝不阻断或拖慢业务。
