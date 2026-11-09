# RequestReplay

Java 请求采集组件 + Python Tkinter 请求回放桌面端。

## 目录

| 目录 | 说明 |
| --- | --- |
| `request-recorder/` | **重构后的采集组件**：多模块 Maven Starter（core + Boot 2/3 适配 + 集成测试 + 中文文档），推荐使用 |
| `java/` | 早期单工程形态的采集组件与 Boot 2 / Boot 3 演示工程（保留作参考） |
| `python/` | Tkinter 回放客户端：请求列表、请求/响应详情、编辑并回放、回放历史 |
| `sql/` | MySQL 8.0 建表脚本（`request_recorder.sql`） |

## request-recorder（推荐）

标准 Maven Starter 形态，业务工程引入一个依赖 + 一段 yml 即可自动采集：

| 模块 | 说明 |
| --- | --- |
| `request-recorder-core` | 纯 Java 核心：配置、模型、异步队列、独立连接池、脱敏、仓储、采集判定（无 Spring / Servlet 依赖） |
| `request-recorder-spring-boot-starter` | 版本无关公共自动装配 |
| `request-recorder-spring-boot-2-starter` | Boot 2 适配（`javax.servlet` + `spring.factories`） |
| `request-recorder-spring-boot-3-starter` | Boot 3 适配（`jakarta.servlet` + `AutoConfiguration.imports`） |
| `request-recorder-integration-tests-boot2` / `-boot3` | 端到端测试（Boot 2.7.18 / Boot 3.3.0），不发布 |

```xml
<dependency>
    <groupId>com.requestrecorder</groupId>
    <artifactId>request-recorder-spring-boot-3-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

```yaml
request-recorder:
  project-code: my-service
  database:
    jdbc-url: jdbc:mysql://127.0.0.1:3306/request_recorder?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: recorder
    password: change-me
```

文档：`request-recorder/docs/使用说明.md`、`配置说明.md`、`发布说明.md`、`兼容性说明.md`。

## 技术与边界

- Boot 2 适配使用 `javax.servlet`，Boot 3 适配使用 `jakarta.servlet`，不能在同一个业务项目同时引入。
- 两套适配依赖公共 core；独立 JDBC/Hikari 连接池，不使用业务 DataSource、事务或 MyBatis。
- Filter 缓存请求/响应文本内容，响应最终通过 `copyBodyToResponse()` 原样写回，异常路径同样写回。
- 有界异步队列写库；队列满/数据库异常时丢弃记录并记录日志与计数，不影响业务响应。
- Python GUI 直接访问独立 MySQL 8。回放支持编辑 URL、Method、Headers、Body；结果写入回放历史。
- 回放添加 `X-Request-Replay: true`，Java 侧跳过该请求的采集，防止回环。

## 初始化

1. 执行 `sql/request_recorder.sql`（可重复执行，老库会自动补 `updated_at` 列）。
2. Java：在 `request-recorder/` 执行 `mvn clean install`（Boot 3 模块需 JDK 17）。旧版单工程在 `java/` 执行 `mvn clean install`。
3. Python：`cd python && pip install -r requirements.txt && python main.py`。
4. EXE：`pyinstaller --noconfirm --clean --onefile --windowed --name RequestReplay main.py`。

Java 8+ 运行 Boot 2；Java 17+ 运行 Boot 3。MySQL 账号建议仅授予独立库权限。

## 注意

- 默认脱敏 Authorization/Cookie/密码/token/secret/id_card/mobile 等。
- 默认最大采集 1 MiB；非文本请求/响应标记跳过。文件、流式响应、SSE、WebSocket 不保证完整捕获。
- 业务工程推荐配置 `@RestControllerAdvice` 全局异常处理，使采集到的状态码与响应体与真实响应一致（否则容器 `/error` 二次派发会导致记录失真）。
- 回放是真实 HTTP 请求，可能创建订单、删除数据、发消息等；请在测试环境使用，GUI 每次回放均二次确认。
- 回放标记头需由网关透传。若经过不可信代理，应通过可信网络/签名标记防止伪造。
- 此版本以通用 Servlet MVC 为目标；异步 Servlet、响应流、压缩响应、multipart 文件内容需按具体项目单独验证。
