# RequestReplay Java 请求采集 Starter 重构开发任务书

## 一、项目目标

将现有 RequestReplay Java 请求采集模块重构为标准 Maven Starter，实现可复用、自动装配、版本隔离的请求采集组件。

最终要求：

1. 同时支持 Spring Boot 2.x 和 Spring Boot 3.x。
2. Java 端作为独立 Maven 依赖发布。
3. 业务项目只需要引入对应版本的依赖。
4. 通过 application.yml 配置项目编码、项目名称和独立采集数据库，即可启用。
5. 不需要业务项目手动注册 Filter、Interceptor 或 Bean。
6. 不使用业务项目自身的 DataSource、JdbcTemplate、MyBatis 或事务管理器。
7. 请求采集采用异步写入，不能明显影响业务请求。
8. 与现有 Python Tkinter 请求回放 GUI 和 MySQL 数据结构兼容。
9. 保留请求回放防循环机制。

本次任务以现有 RequestReplay 源码为基础进行重构，不是另起炉灶重新设计一套不兼容系统。

---

## 二、最终 Maven 项目结构

建议采用 Maven 多模块结构：

```text
request-recorder/
│
├── pom.xml
│
├── request-recorder-core/
│   ├── pom.xml
│   └── src/main/java/
│       └── com/requestrecorder/core/
│           ├── config/
│           ├── database/
│           ├── model/
│           ├── queue/
│           ├── service/
│           ├── serializer/
│           └── util/
│
├── request-recorder-spring-boot-starter/
│   ├── pom.xml
│   └── src/main/java/
│       └── com/requestrecorder/starter/
│           ├── RecorderAutoConfiguration.java
│           ├── RecorderProperties.java
│           ├── RecorderFilter.java
│           └── RecorderLifecycle.java
│
├── request-recorder-spring-boot-2-starter/
│   ├── pom.xml
│   └── src/main/java/
│
├── request-recorder-spring-boot-3-starter/
│   ├── pom.xml
│   └── src/main/java/
│
├── request-recorder-test/
│   ├── pom.xml
│   └── src/test/
│
└── docs/
    ├── 使用说明.md
    ├── 配置说明.md
    ├── 发布说明.md
    └── 兼容性说明.md
```

### 模块职责

**1. request-recorder-core**

纯 Java 公共核心模块。

负责：

- 请求记录对象。
- 独立数据库连接池。
- 数据库写入工具类。
- 异步任务队列。
- 敏感数据脱敏。
- 请求和响应序列化。
- 异常处理。
- 队列容量控制。
- 资源关闭。

不得依赖 Spring Boot、Servlet API、业务项目数据库组件。

**2. request-recorder-spring-boot-starter**

公共自动装配模块。

负责：

- 配置属性绑定。
- 自动装配条件。
- 公共自动装配逻辑。
- Filter 生命周期管理。
- 公共配置校验。

**3. request-recorder-spring-boot-2-starter**

Spring Boot 2 专用适配器。

使用：

- javax.servlet。
- Spring Boot 2 自动配置机制。
- Spring Boot 2 对应的 Spring Framework API。

最低支持版本建议 Boot 2.7.x。

**4. request-recorder-spring-boot-3-starter**

Spring Boot 3 专用适配器。

使用：

- jakarta.servlet。
- Spring Boot 3 自动配置机制。
- Spring Boot 3 对应的 Spring Framework API。

最低支持版本建议 Boot 3.0.x，并验证 Boot 3.0 至 3.x 的兼容范围。

注意：两个 Starter 不允许同时引入同一个业务项目。

---

## 三、业务项目接入方式

最终目标是让业务项目尽可能简单。

### 1. Spring Boot 2

业务项目只需要增加：

```xml
<dependency>
    <groupId>com.requestrecorder</groupId>
    <artifactId>request-recorder-spring-boot-2-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Spring Boot 3

```xml
<dependency>
    <groupId>com.requestrecorder</groupId>
    <artifactId>request-recorder-spring-boot-3-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 3. 配置 application.yml

业务项目只需要配置：

```yaml
request-recorder:
  enabled: true

  # 项目唯一编码
  project-code: order-service

  # 项目显示名称
  project-name: 订单管理系统

  # 独立采集数据库
  database:
    jdbc-url: jdbc:mysql://127.0.0.1:3306/request_recorder?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: recorder
    password: change-me

  # 异步写入
  async:
    enabled: true
    queue-capacity: 5000
    workers: 2

  # 请求体最大采集大小
  max-body-size: 1048576

  # 排除路径
  exclude-paths:
    - /actuator/**
    - /favicon.ico
```

配置完成后，业务项目正常启动即可自动启用。

不得要求业务项目：

- 手动创建 RecorderFilter。
- 手动创建 RecorderDatabase。
- 手动创建 RecordWriter。
- 手动注册 Servlet Filter。
- 手动调用初始化方法。
- 修改业务项目的数据源配置。

---

## 四、项目编码与项目名称要求

项目编码是整个请求采集系统的核心标识。

要求：

1. 每个业务项目拥有独立 project-code。
2. project-code 不能为空。
3. project-code 应当具有唯一性。
4. project-name 用于 GUI 显示。
5. 同一个项目每条请求记录必须写入对应 project-code。
6. Python GUI 根据 project-code 区分项目。
7. 同一数据库允许多个业务项目共同使用。
8. 项目名称变化不得影响历史记录归属。

建议启动时自动向 http_request_project 注册项目。

如果项目编码不存在，则新增。

如果项目编码已经存在，则更新项目名称。

不得因为注册失败而导致业务服务启动失败。

---

## 五、请求采集功能

使用 Servlet Filter 实现请求和响应采集。

不要只使用 HandlerInterceptor，因为它无法完整覆盖所有请求及响应场景。

### 1. 请求采集

必须记录：

- HTTP Method。
- 完整请求 URL。
- 请求 URI。
- Query Parameters。
- Path Variables（可通过适当方式获取；无法获取时明确留空）。
- 请求 Headers。
- 请求 Body。
- Content-Type。
- 客户端 IP。
- User-Agent。
- 请求时间。
- 请求唯一 ID。
- 项目编码。
- 项目名称。

### 2. 响应采集

必须记录：

- HTTP 状态码。
- Response Headers。
- Response Body。
- 响应大小。
- 请求执行耗时。
- 异常信息。
- 项目编码。
- 请求唯一 ID。

### 3. 请求响应包装

正确使用：

- ContentCachingRequestWrapper。
- ContentCachingResponseWrapper。

重点注意：

1. 请求体必须能够被业务 Controller 正常读取。
2. 响应体必须通过 copyBodyToResponse() 正常返回客户端。
3. 不得因为采集导致响应体丢失。
4. 业务抛出异常时也必须尽可能记录请求信息。
5. 必须考虑响应编码。
6. 必须考虑响应体大小限制。
7. 不得无限制缓存大文件。
8. 不采集 WebSocket、SSE、文件下载等不适合完整缓存的内容。
9. 对 multipart 上传应提供明确的采集策略。
10. 不允许在异常情况下覆盖业务原始异常。

### 4. 敏感数据脱敏

默认脱敏：

Headers：

- Authorization
- Cookie
- Set-Cookie
- X-API-Key

JSON/Form 参数：

- password
- passwd
- token
- access_token
- refresh_token
- secret

要求支持自定义脱敏字段。

不得将敏感信息原样写入数据库。

---

## 六、独立数据库模块

这是本项目的重要架构要求。

采集数据库必须完全独立于业务项目。

禁止使用：

- 业务项目 DataSource。
- 业务项目 JdbcTemplate。
- 业务项目 MyBatis。
- 业务项目事务管理器。
- 业务项目数据库连接配置自动推断。

必须自行创建独立连接池。

推荐 HikariCP，但必须使用独立实例和独立配置。

数据库连接配置必须由：

```yaml
request-recorder.database
```

单独管理。

要求：

1. 独立连接池。
2. 独立数据库连接参数。
3. 独立连接池名称。
4. 独立连接超时。
5. 独立连接异常处理。
6. 独立资源释放。
7. 支持数据库暂时不可用。
8. 数据库异常不能影响业务接口正常响应。

业务项目可以使用 MySQL、Oracle、PostgreSQL 等数据库，而采集数据库统一使用 MySQL 8.0。

---

## 七、异步写入机制

请求采集不得同步等待数据库写入。

实现：

```text
HTTP 请求
    |
    v
请求包装
    |
    v
业务 Controller
    |
    v
响应包装
    |
    v
构建请求记录对象
    |
    v
异步队列
    |
    v
独立数据库 Writer
    |
    v
MySQL 8.0
```

要求：

1. 使用有界队列。
2. 默认容量 5000。
3. 支持配置 Worker 数量。
4. 支持队列满时的处理策略。
5. 不允许队列无限增长。
6. 数据库异常不得向业务线程传播。
7. 记录丢弃数量。
8. 提供队列积压数量监控。
9. 应用关闭时执行优雅停止。
10. 在规定等待时间后关闭线程池。
11. 不得因数据库连接异常造成线程永久阻塞。

建议默认策略：

- 队列满：丢弃新采集记录。
- 数据库失败：有限次数重试。
- 失败记录：记录错误日志和统计数据。
- 业务请求：始终优先保证正常执行。

---

## 八、请求回放防循环

Python GUI 回放请求时增加：

```http
X-Request-Replay: true
X-Request-Replay-Id: 123
```

Java Filter 必须在执行采集逻辑之前检查：

```java
X-Request-Replay == true
```

如果存在该请求头：

- 不进入采集队列。
- 不写入请求采集表。
- 不影响业务请求正常执行。

注意：

不能只依赖请求 ID 判断回环。

必须明确通过专用回放标识头跳过采集。

同时提供配置项：

```yaml
request-recorder:
  replay:
    skip-enabled: true
    header-name: X-Request-Replay
    header-value: "true"
```

如果请求经过网关或代理，必须验证该标记头能够透传。

---

## 九、自动装配要求

这是本次重构的重点。

### Spring Boot 2

使用：

META-INF/spring.factories

注册自动配置类。

### Spring Boot 3

使用：

META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

注册自动配置类。

### 自动装配条件

满足以下条件才启用：

- 对应 Servlet Web 环境。
- request-recorder.enabled=true。
- 独立数据库配置有效。
- 项目编码有效。

支持：

```yaml
request-recorder:
  enabled: false
```

一键关闭所有采集功能。

如果未配置数据库或项目编码，应给出清晰的配置错误或禁用提示，不得悄悄启动一个无法工作的采集组件。

同时提供：

```yaml
request-recorder:
  enabled: false
```

作为业务项目临时关闭采集的方式。

---

## 十、数据库兼容性

继续使用现有 RequestReplay 数据库结构。

核心表：

1. http_request_project
2. http_request_record
3. http_replay_history

如果需要增加字段，必须同步更新：

- SQL 初始化文件。
- Java RecordData。
- Java 数据库写入工具。
- Python GUI 查询。
- Python GUI 详情显示。
- Python 回放功能。

不能出现 Java 写入字段与 Python 查询字段不一致的问题。

---

## 十一、Maven 发布要求

支持两种使用方式：

### 本地安装

```bash
mvn clean install
```

### 发布到私服

支持 Nexus、Artifactory 等 Maven 仓库。

提供完整发布说明。

建议统一版本号：

```text
request-recorder-core: 1.0.0
request-recorder-spring-boot-2-starter: 1.0.0
request-recorder-spring-boot-3-starter: 1.0.0
```

所有模块必须保持版本一致。

禁止 Boot 2 Starter 依赖 jakarta.servlet。

禁止 Boot 3 Starter 依赖 javax.servlet。

公共 core 不得引入任何 Servlet API。

---

## 十二、测试要求

必须建立自动化测试，不允许只检查源码是否存在。

### 1. Spring Boot 2 测试

验证：

- Starter 能否正常引入。
- 自动装配是否生效。
- 请求是否采集。
- 请求 Body 是否正确。
- 响应 Body 是否正确。
- 异常请求是否记录。
- 回放请求是否跳过。
- 数据库异常是否不影响业务。

### 2. Spring Boot 3 测试

执行同样的测试。

### 3. 多项目测试

启动两个不同项目：

```yaml
project-code: project-a
```

```yaml
project-code: project-b
```

确认两者写入同一个 MySQL 数据库，但 project-code 不混淆。

### 4. 异步测试

验证：

- 高并发请求。
- 队列满。
- 数据库断开。
- 数据库恢复。
- 应用正常关闭。
- 采集数据丢弃统计。

### 5. 回环测试

验证：

Python 回放请求 -> Java 业务接口 -> Java Filter 检查回放头 -> 不写入采集表。

必须验证数据库中的采集记录数量没有因为回放而增加。

---

## 十三、最终交付物

必须完整交付：

1. 完整 Maven 多模块源码。
2. Boot 2 Starter。
3. Boot 3 Starter。
4. Core 公共模块。
5. MySQL 8.0 完整建表 SQL。
6. application.yml 配置示例。
7. Maven 本地安装说明。
8. Maven 私服发布说明。
9. Spring Boot 2 接入示例。
10. Spring Boot 3 接入示例。
11. 完整自动化测试。
12. 性能及异步队列测试。
13. Python GUI 兼容性检查。
14. 完整中文使用文档。
15. 版本兼容性说明。

所有文件必须提供完整源码，不允许省略，不允许用伪代码替代。

---

## 十四、开发执行顺序

按照以下顺序执行：

第一阶段：分析现有 RequestReplay 项目源码和数据库结构，明确兼容性问题。

第二阶段：重构公共 Core，完成独立数据库工具类和异步写入。

第三阶段：完成公共 Starter 和 Boot 2 自动装配。

第四阶段：完成 Boot 3 自动装配。

第五阶段：完善请求与响应采集、脱敏和回放防循环。

第六阶段：建立 Boot 2、Boot 3 自动化测试项目。

第七阶段：验证 MySQL 8.0 与现有 Python GUI 完全兼容。

第八阶段：完成 Maven 发布和中文文档。

第九阶段：执行完整构建和测试，修复所有问题。

最终必须提供：

- 完整项目目录树。
- 所有源码。
- Maven 构建结果。
- 自动化测试结果。
- 两个 Spring Boot 版本的接入示例。
- 实际运行验证说明。

不要只输出设计方案，本任务要求实际完成全部代码重构。

如发现现有代码存在缺陷，直接修复，并在最终报告中列出修改内容和验证结果。

**最终验收标准：一个全新的 Spring Boot 2 或 Spring Boot 3 业务项目，只需要引入对应 Maven Starter，配置 project-code、project-name 和独立数据库连接，即可自动采集 HTTP 请求和响应。业务项目不需要编写任何请求采集代码。**
