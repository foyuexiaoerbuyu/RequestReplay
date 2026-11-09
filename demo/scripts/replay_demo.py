#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""RequestRecorder 回放链路演示（与 Python GUI 复用同一套 replay 逻辑）。

从采集库取最近一次 /api/hello(GET) 与 /api/order(POST) 记录，
按原始 method / headers / body 向 demo 服务重放，写入 http_replay_history，
并验证两点：回放响应状态与原始一致；回放请求不会被再次采集（防回环）。

用法：
    python replay_demo.py
    python replay_demo.py --project demo-boot2 --base-url http://127.0.0.1:8091
"""

import argparse
import json
import os
import sys
import time

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

PY_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "python")
sys.path.insert(0, PY_DIR)

from config import load_config          # noqa: E402  GUI 同源配置
from db import DB                       # noqa: E402
from replay import replay, save_history  # noqa: E402  GUI 同源回放引擎

TARGETS = ("/api/hello", "/api/order", "/api/boom")


def pick_records(db, project):
    picked = []
    for uri in TARGETS:
        rows = db.query(
            "SELECT id, project_code, method, uri, query_params, request_headers, request_body, response_status "
            "FROM http_request_record WHERE project_code=%s AND uri=%s ORDER BY id DESC LIMIT 1",
            (project, uri))
        if rows:
            picked.append(rows[0])
    return picked


def count_records(db, project):
    rows = db.query("SELECT COUNT(*) AS c FROM http_request_record WHERE project_code=%s", (project,))
    return rows[0]["c"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8090")
    parser.add_argument("--project", default="demo-boot3")
    args = parser.parse_args()

    db = DB(load_config())
    records = pick_records(db, args.project)
    if not records:
        sys.exit("采集库中没有 {0} 的记录，请先运行 verify_demo.py 产生数据".format(args.project))

    before = count_records(db, args.project)
    print("== 回放 {0} 条记录（回放前采集总量 {1}）==\n".format(len(records), before))

    rows = []
    for record in records:
        url = args.base_url.rstrip("/") + record["uri"]
        if record["query_params"]:
            url += "?" + record["query_params"]
        headers = json.loads(record["request_headers"] or "{}")
        body = record["request_body"]

        result = replay(record, url, record["method"], headers, body)
        save_history(db, record, url, record["method"], result)

        same_status = result["response_status"] == record["response_status"]
        rows.append({
            "id": record["id"],
            "method": record["method"],
            "url": url,
            "origin": record["response_status"],
            "replay": result["response_status"],
            "status": result["replay_status"],
            "same": same_status,
            "ms": result["duration_ms"],
        })

    time.sleep(2)
    after = count_records(db, args.project)
    loop_free = after == before

    print("| 源记录 | 方法 | 目标 URL | 原始状态 | 回放状态 | 回放结果 | 一致 | 耗时(ms) |")
    print("| --- | --- | --- | --- | --- | --- | --- | --- |")
    for row in rows:
        print("| {id} | {method} | {url} | {origin} | {replay} | {status} | {same} | {ms} |".format(
            id=row["id"], method=row["method"], url=row["url"], origin=row["origin"],
            replay=row["replay"], status=row["status"], same="是" if row["same"] else "否", ms=row["ms"]))

    print("\n| 校验项 | 结果 | 说明 |")
    print("| --- | --- | --- |")
    all_ok = all(row["status"] == "SUCCESS" and row["same"] for row in rows)
    print("| 回放均成功且状态与原始一致 | {0} | {1}/{2} 条一致 |".format(
        "通过" if all_ok else "失败", sum(1 for r in rows if r["same"]), len(rows)))
    print("| 防回环：回放不产生新采集 | {0} | {1} -> {2} |".format(
        "通过" if loop_free else "失败", before, after))

    if not (all_ok and loop_free):
        return 1
    print("\n结论：采集 -> 回放 闭环打通，Python GUI 打开后选 {0} 即可看到这些记录与回放历史。".format(args.project))
    return 0


if __name__ == "__main__":
    sys.exit(main())
