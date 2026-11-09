---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: 58e87cd5ac29bab7322874bd6b207f1c_79ed59f4dc0711f1bff2525400cd780f
    ReservedCode1: MvfI+CL0Ky3OP3e2vbiHGrs3xAs+NU1C2h0aHXkRVwnuipAW/c09mTUIy6Y2GHLKeLAQSZNZ07u5TdpHjEs/IEZ1YoNtRGPxDYJo0oDXQdK6e4bp6NdO1RRm3B2ERMzmN0PYpyrUnmtB9sF81vf+6lMs8+UxlVxPca02v0VZoMLL6QllNA85AKMWeg0=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: 58e87cd5ac29bab7322874bd6b207f1c_79ed59f4dc0711f1bff2525400cd780f
    ReservedCode2: MvfI+CL0Ky3OP3e2vbiHGrs3xAs+NU1C2h0aHXkRVwnuipAW/c09mTUIy6Y2GHLKeLAQSZNZ07u5TdpHjEs/IEZ1YoNtRGPxDYJo0oDXQdK6e4bp6NdO1RRm3B2ERMzmN0PYpyrUnmtB9sF81vf+6lMs8+UxlVxPca02v0VZoMLL6QllNA85AKMWeg0=
---

# RequestRecorder 最小端到端演示工程

一个"最小可运行"的 Spring Boot 示例，用来演示业务系统接入 RequestRecorder 采集 Starter 后，
**请求自动入库 -> Python GUI 查询/详情 -> 原样回放 -> 回放历史落库** 的完整链路。

业务侧需要做的事只有两件：**引一个 starter 依赖 + 写一段 yml 配置**，不需要写任何采集代码。

---

## 一、目录结构

```
demo/
├── pom.xml                      聚合父 pom（modules: demo-boot2 / demo-boot3）
├── demo-boot3/                  Spring Boot 3.3.0 + JDK 17，端口 8090
├── demo-boot2/                  Spring Boot 2.7.18 + JDK 8，端口 8091
│   └── src/main/
│       ├── java/com/example/demo/
│       │   ├── DemoApplication.java          启动类（无采集相关代码）
│       │   ├── DemoController.java           6 个演示接口
│       │   └── GlobalExceptionHandler.java   保证 500 被正确采集
│       └── resources/application.yml         采集配置（接入的全部工作量）
└── scripts/
    ├── verify_demo.py            采集链路验证：打接口 -> 查库 -> 逐项断言
    └── replay_demo.py            回放链路验证：取记录 -> 复用 GUI 引擎回放 -> 校验
```

## 二、环境要求

| 依赖 | 说明 |
| --- | --- |
| JDK | demo-boot3 用 JDK 17；demo-boot2 用 JDK 8 |
| MySQL | 采集库，默认 `127.0.0.1:3306/request_recorder`（账号密码在 yml / 环境变量中配） |
| Python | 3.8+，`pip install mysql-connector-python requests`（GUI 同款依赖） |
| 组件 | 先安装 starter 到本地仓库：`mvn -f request-recorder/pom.xml clean install -DskipTests` |

## 三、三步跑起来

```bash
# 0) 安装采集 Starter 到本地 Maven 仓库（首次或改过组件代码后执行）
cd request-recorder
mvn clean install -DskipTests

# 1) 启动 demo（二选一）
cd demo && mvn -pl demo-boot3 package -DskipTests
java -jar demo-boot3/target/demo-boot3-1.0.0.jar          # 8090
java -jar demo-boot2/target/demo-boot2-1.0.0.jar          # 8091（需 JDK 8）

# 2) 采集链路验证
cd demo/scripts
python verify_demo.py --project demo-boot3 --base-url http://127.0.0.1:8090

# 3) 回放链路验证（与 Python GUI 复用同一套 replay 引擎）
python replay_demo.py --project demo-boot3 --base-url http://127.0.0.1:8090

# 4) 打开 GUI 肉眼确认
cd python && python main.py     # 项目选 demo-boot3 / demo-boot2
```

数据库连接优先级：命令行参数 > 环境变量 `RR_IT_MYSQL_*` > `%APPDATA%\RequestReplay\config.json`。

## 四、演示接口（覆盖六类采集场景）

| 接口 | 场景 | 预期采集结果 |
| --- | --- | --- |
| `GET /api/hello?name=demo` | 正常请求 | status=200，query 与响应体完整 |
| `POST /api/order` | JSON 请求体 | request_body 完整入库，含中文不变码 |
| `GET /api/boom` | 业务异常 | status=500，响应体为真实业务 JSON |
| `GET /api/large?kb=512` | 大响应 | 超过 max-body-size 截断，response_truncated=1 |
| `GET /api/secure` | 敏感信息 | Authorization 头与 token 落库为 `******` |
| `GET /api/skip/ping` | 排除路径 | 业务正常返回，但**不产生**采集记录 |

## 五、接入配置（application.yml 关键片段）

```yaml
request-recorder:
  enabled: true
  project-code: demo-boot3        # 项目唯一标识，GUI 下拉即按此维度
  project-name: RequestRecorder 最小演示
  auto-init-schema: true          # 首次运行自动建表，表已存在无副作用
  max-body-size: 262144           # 256KB，超出截断并打标
  exclude-paths: /api/skip/**     # 排除路径不采集
  record-static-resources: false
  database:
    jdbc-url: jdbc:mysql://127.0.0.1:3306/request_recorder?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: ${DEMO_DB_USER:root}
    password: ${DEMO_DB_PASSWORD:root}
    pool-size: 3
  async:
    enabled: true                 # 异步落库，不阻塞业务线程
    workers: 2
    queue-capacity: 500
```

数据库驱动与连接池在 Starter 中为 `optional`，业务工程按需显式引入：

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
</dependency>
```

## 六、实测结果（本机已跑通）

采集链路（`verify_demo.py`）：

| 校验项 | Boot 3 | Boot 2 |
| --- | --- | --- |
| 项目登记到 http_request_project | 通过 | 通过 |
| /api/hello 采集且 status=200 | 通过 | 通过 |
| /api/order 请求体入库 | 通过 | 通过 |
| /api/boom 采集到真实 500 | 通过 | 通过 |
| /api/large 超限截断 | 通过 | 通过 |
| /api/secure 的 Authorization 脱敏 | 通过 | 通过 |
| 排除路径未被采集 | 通过 | 通过 |

回放链路（`replay_demo.py`）：3/3 条记录回放 SUCCESS，响应状态与原始完全一致，
且回放前后采集总量不变（36 -> 36 / 43 -> 43），**防回环生效**。

## 七、注意事项

1. **500 必须由 `@RestControllerAdvice` 在 Filter 链内写回**。若异常透传到容器 `/error`
   二次派发，采集到的会是 `status=200` + 空响应体，与真实行为不一致（组件已知边界）。
2. **配置绑定**：Starter 的 `RecorderProperties` 需标准 getter/setter；本 demo 直接使用
   官方属性类，无需额外适配类。
3. **表不存在的旧库**：`auto-init-schema: true` 会建表；也可手动执行
   `sql/request_recorder.sql`。历史记录未登记项目时，执行 `sql/backfill_project_from_records.sql`
   才能在 GUI 下拉中看到。
4. **端口冲突**：8090 / 8091 被占用时改 `server.port`，并同步脚本 `--base-url`。
5. **回放目标地址**：脚本按 `--base-url + uri + query` 拼接，指向同一 demo 服务；真实场景
   中应改为被测服务的地址。
*（内容由AI生成，仅供参考）*
