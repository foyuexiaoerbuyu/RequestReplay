# RequestReplay

Java 请求采集组件 + Python Tkinter 请求回放桌面端。

## 技术与边界
- Boot 2 使用 `request-recorder-boot2`（javax.servlet）；Boot 3 使用 `request-recorder-boot3`（jakarta.servlet），不能在同一个业务项目同时引入。
- 两个适配器依赖公共 core；独立 JDBC/Hikari 连接，不使用业务 DataSource。
- Filter 缓存请求/响应文本内容，响应最终通过 `copyBodyToResponse()` 原样写回。
- 有界异步队列写库；队列满/数据库异常时丢弃记录并记录日志，不影响业务响应。
- Python GUI 直接访问独立 MySQL 8。回放支持编辑 URL、Method、Headers、Body；结果写入回放历史。
- 回放添加 `X-Request-Replay: true`，Java Filter 跳过该请求，防止回环。

## 初始化
1. 执行 `sql/request_recorder.sql`。
2. Java：在 `java/` 执行 `mvn clean install`。Boot 2 项目依赖 `request-recorder-boot2`；Boot 3 项目依赖 `request-recorder-boot3`。示例配置见 `java/application-recorder-example.yml`。
3. Python：`cd python && pip install -r requirements.txt && python main.py`。
4. EXE：`pyinstaller --noconfirm --clean --onefile --windowed --name RequestReplay main.py`。

Java 8+ 运行 Boot2；Java 17+ 运行 Boot3。MySQL 账号建议仅授予独立库权限。

## 注意
- 默认脱敏 Authorization/Cookie/密码/token/secret。
- 默认最大采集 1 MiB；非文本请求/响应标记跳过。文件、流式响应、SSE、WebSocket 不保证完整捕获。
- 回放是真实 HTTP 请求，可能创建订单、删除数据、发消息等；请在测试环境使用，GUI 每次回放均二次确认。
- 回放标记头需由网关透传。若经过不可信代理，应通过可信网络/签名标记防止伪造。
- 此版本以通用 Servlet MVC 为目标；异步 Servlet、响应流、压缩响应、multipart 文件内容需按具体项目单独验证。
