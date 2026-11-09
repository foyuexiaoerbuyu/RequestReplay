#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
RequestRecorder 与 Python Tkinter 回放客户端（python/main.py）的兼容性校验脚本。

校验内容（全部只读，不写库、不删数据）：
1. 连接 Python GUI 使用的同一 MySQL 库（读取 %APPDATA%/RequestReplay/config.json）。
2. 检查 GUI 依赖的表与字段是否齐全（http_request_project / http_request_record / http_replay_history）。
3. 检查 Java 采集组件 RecorderRepository 的 INSERT / UPDATE 语句所涉及的列是否全部存在。
4. 实际执行 GUI 使用的四条查询语句，确认可执行且字段可用。

用法：
    python verify_gui_compat.py                  # 使用 GUI 配置文件
    python verify_gui_compat.py --host 127.0.0.1 --port 3306 --database request_recorder --user root --password xxx
    python verify_gui_compat.py --repository D:\\path\\RecorderRepository.java

退出码：0 兼容；1 存在不兼容项；2 环境或连接问题。
"""

from __future__ import print_function

import argparse
import json
import os
import re
import sys

try:
    import mysql.connector
except ImportError:
    print("缺少依赖 mysql-connector-python，请先执行：pip install mysql-connector-python")
    sys.exit(2)

# GUI 四条查询语句（与 python/main.py 保持一致）
GUI_QUERIES = {
    "项目下拉列表": "SELECT project_code,project_name FROM http_request_project ORDER BY project_name",
    "项目列表": "SELECT * FROM http_request_project ORDER BY id DESC",
    "请求列表": ("SELECT id,project_code,request_time,method,uri,response_status,duration_ms,client_ip "
             "FROM http_request_record ORDER BY id DESC LIMIT 1"),
    "回放历史": "SELECT * FROM http_replay_history ORDER BY id DESC LIMIT 1",
}

# GUI 明确引用的列（main.py 的 Treeview 与详情面板）
GUI_REQUIRED_COLUMNS = {
    "http_request_project": ["project_code", "project_name"],
    "http_request_record": ["id", "project_code", "request_time", "method", "uri", "url",
                            "response_status", "duration_ms", "client_ip"],
    "http_replay_history": ["id", "source_record_id", "project_code", "target_url", "method",
                            "replay_time", "replay_status", "response_status"],
}

# 采集组件写入所需列（RecorderRepository 的 INSERT/UPDATE 列；脚本会再与源码解析结果合并）
REPOSITORY_FALLBACK_COLUMNS = {
    "http_request_project": ["project_code", "project_name", "description", "created_at", "updated_at"],
    "http_request_record": [
        "project_code", "project_name", "request_id", "request_time", "method", "url", "uri",
        "query_params", "path_params", "request_headers", "request_body", "client_ip", "user_agent",
        "response_status", "response_headers", "response_body", "duration_ms", "response_size",
        "exception_message", "request_truncated", "response_truncated", "created_at",
    ],
}


def load_gui_config():
    appdata = os.getenv("APPDATA") or os.path.expanduser("~")
    cfg_file = os.path.join(appdata, "RequestReplay", "config.json")
    default = {"host": "127.0.0.1", "port": 3306, "database": "request_recorder",
               "user": "root", "password": "", "charset": "utf8mb4"}
    if os.path.exists(cfg_file):
        try:
            with open(cfg_file, "r", encoding="utf-8") as handle:
                data = json.load(handle)
            default.update({k: v for k, v in data.items() if v is not None})
        except Exception as exc:  # noqa: BLE001
            print("[警告] 读取 GUI 配置失败，使用默认值：%s" % exc)
    return default


def parse_repository_columns(path):
    """从 RecorderRepository.java 里解析 INSERT/UPDATE 语句涉及的列名。"""
    result = {"http_request_project": set(), "http_request_record": set()}
    if not path or not os.path.exists(path):
        return result
    with open(path, "r", encoding="utf-8", errors="replace") as handle:
        source = handle.read()

    # 提取字符串常量中的 SQL 片段（Java 双引号 / 文本块）
    sql_blocks = re.findall(r'"([^"]*(?:INSERT|UPDATE)(?:[^"]|\\")*)"', source, flags=re.IGNORECASE)
    for sql in sql_blocks:
        for table, columns in re.findall(
                r"(?:INSERT\s+INTO|UPDATE)\s+`?(http_request_[a-z_]+)`?\s*(?:\(([^)]*)\))?",
                sql, flags=re.IGNORECASE):
            if columns:
                for column in columns.split(","):
                    name = column.strip().strip("`").strip()
                    if name and re.match(r"^[A-Za-z_][A-Za-z0-9_]*$", name):
                        result.setdefault(table, set()).add(name)
    return result


def fetch_columns(connection, database):
    cursor = connection.cursor()
    try:
        cursor.execute(
            "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema=%s",
            (database,))
        columns = {}
        for table, column in cursor.fetchall():
            columns.setdefault(table, set()).add(column)
        return columns
    finally:
        cursor.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host")
    parser.add_argument("--port", type=int)
    parser.add_argument("--database")
    parser.add_argument("--user")
    parser.add_argument("--password")
    parser.add_argument("--repository", help="RecorderRepository.java 路径，用于校验写入列")
    args = parser.parse_args()

    config = load_gui_config()
    if args.host:
        config["host"] = args.host
    if args.port:
        config["port"] = args.port
    if args.database:
        config["database"] = args.database
    if args.user:
        config["user"] = args.user
    if args.password is not None:
        config["password"] = args.password

    print("目标数据库: %s:%s/%s user=%s" % (config["host"], config["port"], config["database"], config["user"]))

    try:
        connection = mysql.connector.connect(
            host=config["host"], port=int(config["port"]), database=config["database"],
            user=config["user"], password=config.get("password", ""),
            charset=config.get("charset", "utf8mb4"), connection_timeout=8)
    except Exception as exc:  # noqa: BLE001
        print("[失败] 无法连接数据库：%s" % exc)
        return 2

    problems = []
    try:
        cursor = connection.cursor(dictionary=True)
        columns = fetch_columns(connection, config["database"])

        print("\n== 1. GUI 依赖字段 ==")
        for table, required in GUI_REQUIRED_COLUMNS.items():
            existing = columns.get(table)
            if not existing:
                problems.append("缺少表 %s" % table)
                print("  [缺失] 表 %s" % table)
                continue
            missing = [column for column in required if column not in existing]
            if missing:
                problems.append("表 %s 缺少列 %s" % (table, ",".join(missing)))
                print("  [缺失] %s -> %s" % (table, ", ".join(missing)))
            else:
                print("  [通过] %s 共 %d 列，GUI 所需列齐全" % (table, len(existing)))

        print("\n== 2. 采集组件写入字段 ==")
        parsed = parse_repository_columns(args.repository)
        for table in sorted(REPOSITORY_FALLBACK_COLUMNS):
            required = set(REPOSITORY_FALLBACK_COLUMNS[table]) | parsed.get(table, set())
            existing = columns.get(table, set())
            missing = sorted(column for column in required if column not in existing)
            if missing:
                problems.append("表 %s 缺少采集写入列 %s" % (table, ",".join(missing)))
                print("  [缺失] %s -> %s" % (table, ", ".join(missing)))
            else:
                print("  [通过] %s 共 %d 列，采集写入列齐全%s" % (
                    table, len(existing),
                    "（源码解析补充 %d 列）" % len(parsed.get(table, set())) if parsed.get(table) else ""))

        print("\n== 3. GUI 查询语句可执行性 ==")
        for name, sql in GUI_QUERIES.items():
            try:
                cursor.execute(sql)
                rows = cursor.fetchall()
                print("  [通过] %s：返回 %d 行" % (name, len(rows)))
            except Exception as exc:  # noqa: BLE001
                problems.append("GUI 查询失败（%s）：%s" % (name, exc))
                print("  [失败] %s：%s" % (name, exc))

        print("\n== 4. 数据抽样 ==")
        try:
            cursor.execute("SELECT COUNT(*) AS total FROM http_request_record")
            print("  http_request_record 记录数: %s" % cursor.fetchone()["total"])
            cursor.execute("SELECT COUNT(DISTINCT project_code) AS total FROM http_request_project")
            print("  http_request_project 项目数: %s" % cursor.fetchone()["total"])
        except Exception as exc:  # noqa: BLE001
            problems.append("抽样统计失败：%s" % exc)
            print("  [失败] %s" % exc)
        cursor.close()
    finally:
        connection.close()

    print("\n== 结论 ==")
    if problems:
        for item in problems:
            print("  - %s" % item)
        print("结果：存在 %d 项不兼容，请先修正后再使用 GUI。" % len(problems))
        return 1
    print("结果：完全兼容 —— Java 采集写入字段与 Python GUI 读取字段一致，查询语句均可正常执行。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
