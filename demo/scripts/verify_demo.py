#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""RequestRecorder 端到端演示验证脚本。

做三件事：
1. 依次请求 demo 工程的六类接口（正常 GET / 正常 POST / 异常 500 / 大响应 / 敏感头 / 排除路径）；
2. 轮询采集库，等待异步线程落库；
3. 逐条断言采集结果，输出 Markdown 表格与结论。

用法：
    python verify_demo.py                        # 默认校验 demo-boot3（8090）
    python verify_demo.py --project demo-boot2 --base-url http://127.0.0.1:8091
    python verify_demo.py --keep                 # 校验后保留本次演示数据（默认也保留，仅打印）
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request

try:
    import mysql.connector
except ImportError:
    sys.exit("缺少依赖：pip install mysql-connector-python")

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

SECRET_TOKEN = "demo-secret-token-1234567890"


def load_db_config():
    """优先读 Python GUI 的 config.json，其次读环境变量。"""
    config_path = os.path.join(os.environ.get("APPDATA", ""), "RequestReplay", "config.json")
    cfg = {}
    if os.path.isfile(config_path):
        with open(config_path, "r", encoding="utf-8") as fh:
            cfg = json.load(fh)
    return {
        "host": os.environ.get("RR_IT_MYSQL_HOST") or cfg.get("host") or "127.0.0.1",
        "port": int(os.environ.get("RR_IT_MYSQL_PORT") or cfg.get("port") or 3306),
        "user": os.environ.get("RR_IT_MYSQL_USER") or cfg.get("user") or "root",
        "password": os.environ.get("RR_IT_MYSQL_PASSWORD") or cfg.get("password") or "",
        "database": os.environ.get("RR_IT_MYSQL_DATABASE") or cfg.get("database") or "request_recorder",
    }


def http(method, url, body=None, headers=None):
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    for key, value in (headers or {}).items():
        request.add_header(key, value)
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return response.status, response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "replace")


def call_endpoints(base_url):
    """返回 [(场景, 期望状态, 实际状态)] 与失败列表。"""
    calls = []
    calls.append(("正常 GET /api/hello", 200, http("GET", base_url + "/api/hello?name=demo")))
    calls.append(("正常 POST /api/order", 200,
                  http("POST", base_url + "/api/order", body={"sku": "SKU-9001", "qty": 2})))
    calls.append(("异常 500 /api/boom", 500, http("GET", base_url + "/api/boom")))
    calls.append(("大响应 /api/large", 200, http("GET", base_url + "/api/large?kb=512")))
    calls.append(("敏感头 /api/secure", 200,
                  http("GET", base_url + "/api/secure?token=" + SECRET_TOKEN,
                       headers={"Authorization": "Bearer " + SECRET_TOKEN})))
    calls.append(("排除路径 /api/skip/ping", 200, http("GET", base_url + "/api/skip/ping")))
    return [(name, expect, actual[0]) for name, expect, actual in calls]


def fetch_records(db, project):
    conn = mysql.connector.connect(host=db["host"], port=db["port"], user=db["user"],
                                   password=db["password"], database=db["database"], charset="utf8mb4")
    try:
        cursor = conn.cursor(dictionary=True)
        cursor.execute(
            "SELECT id, method, uri, response_status, response_body, request_headers, "
            "query_params, exception_message, response_truncated, duration_ms "
            "FROM http_request_record WHERE project_code=%s ORDER BY id DESC", (project,))
        records = cursor.fetchall()
        cursor.execute("SELECT project_name FROM http_request_project WHERE project_code=%s", (project,))
        project_row = cursor.fetchone()
        cursor.close()
        return records, project_row
    finally:
        conn.close()


def wait_records(db, project, expected, timeout=15):
    deadline = time.time() + timeout
    records = []
    while time.time() < deadline:
        records, project_row = fetch_records(db, project)
        if len(records) >= expected:
            return records, project_row
        time.sleep(0.5)
    return records, None


def latest(records, uri):
    for record in records:
        if record["uri"] == uri:
            return record
    return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8090")
    parser.add_argument("--project", default="demo-boot3")
    parser.add_argument("--keep", action="store_true", help="仅保留数据（默认行为，参数保留以便脚本化）")
    args = parser.parse_args()

    db = load_db_config()
    print("采集库: {host}:{port}/{database}  项目: {project}\n".format(project=args.project, **db))

    print("== 1. 发送演示请求 ==")
    results = call_endpoints(args.base_url)
    for name, expect, actual in results:
        print("  [{flag}] {name}: 期望 {expect}, 实际 {actual}".format(
            flag="通过" if expect == actual else "异常", name=name, expect=expect, actual=actual))

    print("\n== 2. 等待异步落库 ==")
    records, project_row = wait_records(db, args.project, expected=5)
    print("  已落库记录数: {count}".format(count=len(records)))

    print("\n== 3. 断言采集结果 ==")
    checks = []

    checks.append(("项目已登记到 http_request_project", project_row is not None,
                   project_row["project_name"] if project_row else "未登记"))

    hello = latest(records, "/api/hello")
    checks.append(("/api/hello 被采集且 status=200", hello is not None and hello["response_status"] == 200,
                   "无记录" if hello is None else "status={0}".format(hello["response_status"])))

    order = latest(records, "/api/order")
    checks.append(("/api/order 请求体入库", order is not None and "SKU-9001" in (order["response_body"] or ""),
                   "无记录" if order is None else (order["response_body"] or "")[:60]))

    boom = latest(records, "/api/boom")
    boom_body = (boom["response_body"] if boom else "") or ""
    checks.append(("/api/boom 采集到真实 500",
                   boom is not None and boom["response_status"] == 500 and "demo-boom" in boom_body,
                   "无记录" if boom is None else "status={0}, body={1}".format(
                       boom["response_status"], boom_body[:40])))

    large = latest(records, "/api/large")
    checks.append(("/api/large 超限被截断", large is not None and int(large["response_truncated"] or 0) == 1,
                   "无记录" if large is None else "truncated={0}".format(large["response_truncated"])))

    secure = latest(records, "/api/secure")
    headers_json = (secure["request_headers"] if secure else "") or ""
    checks.append(("/api/secure 的 Authorization 已脱敏",
                   secure is not None and "******" in headers_json and SECRET_TOKEN not in headers_json,
                   "无记录" if secure is None else ("含明文" if SECRET_TOKEN in headers_json else "已脱敏")))

    skipped = [r for r in records if (r["uri"] or "").startswith("/api/skip")]
    checks.append(("排除路径 /api/skip/** 未被采集", len(skipped) == 0,
                   "采到 {0} 条".format(len(skipped))))

    print("| 校验项 | 结果 | 说明 |")
    print("| --- | --- | --- |")
    for name, ok, detail in checks:
        print("| {name} | {result} | {detail} |".format(
            name=name, result="通过" if ok else "失败", detail=str(detail).replace("|", "/").replace("\n", " ")))

    failed = [name for name, ok, _ in checks if not ok]
    print("")
    if failed:
        print("结论：{0} 项未通过 —— {1}".format(len(failed), "；".join(failed)))
        print("提示：确认 demo 已启动、application.yml 的采集库账号正确、auto-init-schema=true。")
        return 1

    print("结论：全部通过，采集组件端到端可用（业务接口 -> 采集库 -> Python GUI 可回放）。")
    print("下一步：打开 Python GUI（python/main.py），项目选 {0}，选任意记录点「回放」。".format(args.project))
    return 0


if __name__ == "__main__":
    sys.exit(main())
