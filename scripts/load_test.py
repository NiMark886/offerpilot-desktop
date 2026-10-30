#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OfferPilot 完整链路压测。

每个虚拟用户独立执行：
注册 -> 上传简历 -> 创建面试 -> 开始 -> SSE 答题 -> 结束 -> 生成报告

默认针对 Mock AI，报告中不包含真实 LLM 延迟。
"""

import argparse
import concurrent.futures
import io
import json
import os
import platform
import statistics
import threading
import time
import uuid
import zipfile
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

import requests

RESUME_LINES = [
    "压测用户",
    "Java 后端开发工程师，3 年经验",
    "技能：Java, Spring Boot, MySQL, Redis, 微服务",
    "项目：OfferPilot AI 面试平台，负责后端架构与 RAG 检索。",
]


def build_resume_docx(index: int) -> bytes:
    content_types = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Override PartName="/word/document.xml" '
        'ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
        '</Types>'
    )
    rels = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" '
        'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" '
        'Target="word/document.xml"/></Relationships>'
    )
    lines = [f"{RESUME_LINES[0]} {index}"] + RESUME_LINES[1:]
    paragraphs = "".join(
        f"<w:p><w:r><w:t>{line}</w:t></w:r></w:p>" for line in lines
    )
    document = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
        f"<w:body>{paragraphs}</w:body></w:document>"
    )
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("[Content_Types].xml", content_types)
        archive.writestr("_rels/.rels", rels)
        archive.writestr("word/document.xml", document)
    return buffer.getvalue()


@dataclass
class StepResult:
    name: str
    seconds: float
    ok: bool
    error: str = ""


@dataclass
class UserResult:
    user_index: int
    seconds: float
    ok: bool
    error: str = ""
    steps: list[StepResult] = field(default_factory=list)


def api_data(response: requests.Response):
    try:
        body = response.json()
    except ValueError as exc:
        raise RuntimeError(
            f"non-json response status={response.status_code} body={response.text[:200]}"
        ) from exc
    if response.status_code >= 400 or body.get("code") != 0:
        raise RuntimeError(
            f"status={response.status_code} code={body.get('code')} message={body.get('message')}"
        )
    return body["data"]


class VirtualUser:
    def __init__(self, index: int, base_url: str, run_id: str):
        self.index = index
        self.base_url = base_url.rstrip("/")
        self.run_id = run_id
        self.session = requests.Session()
        self.steps: list[StepResult] = []

    def run(self) -> UserResult:
        started = time.perf_counter()
        try:
            token = self.step("register", self._register)
            self.session.headers.update({"Authorization": f"Bearer {token}"})
            resume_id = self.step("resume_upload", self._upload_resume)
            job_id = self.step("job_templates", self._job_templates)
            session_id = self.step("interview_create", lambda: self._create_interview(job_id, resume_id))
            question_id = self.step("interview_start", lambda: self._start_interview(session_id))
            self.step("answer_sse", lambda: self._answer(session_id, question_id))
            self.step("interview_finish", lambda: self._finish(session_id))
            self.step("report_generate", lambda: self._report(session_id))
            return UserResult(self.index, time.perf_counter() - started, True, steps=self.steps)
        except Exception as exc:  # noqa: BLE001 - 压测需要记录任意失败
            return UserResult(
                self.index, time.perf_counter() - started, False, str(exc), self.steps
            )

    def step(self, name: str, action):
        started = time.perf_counter()
        try:
            value = action()
            self.steps.append(StepResult(name, time.perf_counter() - started, True))
            return value
        except Exception as exc:  # noqa: BLE001
            self.steps.append(StepResult(name, time.perf_counter() - started, False, str(exc)))
            raise

    def _register(self) -> str:
        email = f"load-{self.run_id}-{self.index}@example.com"
        response = self.session.post(
            f"{self.base_url}/api/auth/register",
            json={"email": email, "password": "password123", "nickname": f"LoadUser{self.index}"},
            timeout=30,
        )
        return api_data(response)["accessToken"]

    def _upload_resume(self) -> int:
        files = {
            "file": (
                f"load-{self.index}.docx",
                build_resume_docx(self.index),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            )
        }
        response = self.session.post(
            f"{self.base_url}/api/resumes", files=files, timeout=60
        )
        return api_data(response)["id"]

    def _job_templates(self) -> int:
        response = self.session.get(f"{self.base_url}/api/job-templates", timeout=30)
        return api_data(response)[0]["id"]

    def _create_interview(self, job_id: int, resume_id: int) -> int:
        response = self.session.post(
            f"{self.base_url}/api/interviews",
            json={
                "jobTemplateId": job_id,
                "resumeId": resume_id,
                "difficulty": "STANDARD",
                "durationMinutes": 30,
                "persona": "STANDARD",
                "mode": "INTERVIEW",
            },
            timeout=120,
        )
        return api_data(response)["id"]

    def _start_interview(self, session_id: int) -> int:
        response = self.session.post(
            f"{self.base_url}/api/interviews/{session_id}/start", timeout=30
        )
        return api_data(response)["currentQuestionId"]

    def _answer(self, session_id: int, question_id: int) -> None:
        with self.session.post(
            f"{self.base_url}/api/interviews/{session_id}/answers",
            json={
                "questionId": question_id,
                "content": "HashMap 底层是数组、链表和红黑树，扩容时容量翻倍并重新分布元素。",
            },
            headers={"Accept": "text/event-stream"},
            stream=True,
            timeout=120,
        ) as response:
            if response.status_code >= 400:
                raise RuntimeError(f"SSE status={response.status_code}")
            chunks: list[str] = []
            for chunk in response.iter_content(chunk_size=1024, decode_unicode=True):
                if chunk:
                    chunks.append(chunk if isinstance(chunk, str) else chunk.decode("utf-8", "ignore"))
            body = "".join(chunks)
            if "event:done" not in body.replace(" ", ""):
                raise RuntimeError("SSE stream did not emit done event")

    def _finish(self, session_id: int) -> None:
        response = self.session.post(
            f"{self.base_url}/api/interviews/{session_id}/finish", timeout=30
        )
        api_data(response)

    def _report(self, session_id: int) -> None:
        response = self.session.post(
            f"{self.base_url}/api/interviews/{session_id}/report", timeout=120
        )
        api_data(response)


def percentile(values: list[float], ratio: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, int(round(ratio * (len(ordered) - 1))))
    return round(ordered[index], 3)


def summarize(results: list[UserResult], wall_seconds: float, base_url: str) -> dict:
    step_names = sorted({step.name for result in results for step in result.steps})
    steps: dict[str, dict] = {}
    for name in step_names:
        items = [step for result in results for step in result.steps if step.name == name]
        latencies = [step.seconds for step in items]
        steps[name] = {
            "count": len(items),
            "success": sum(1 for step in items if step.ok),
            "p50Seconds": round(statistics.median(latencies), 3) if latencies else 0.0,
            "p95Seconds": percentile(latencies, 0.95),
            "p99Seconds": percentile(latencies, 0.99),
        }
    success_users = sum(1 for result in results if result.ok)
    users = len(results)
    return {
        "generatedAt": datetime.now().astimezone().isoformat(timespec="seconds"),
        "baseUrl": base_url,
        "users": users,
        "wallSeconds": round(wall_seconds, 3),
        "successUsers": success_users,
        "failedUsers": users - success_users,
        "successRate": round(success_users * 100.0 / users, 2) if users else 0.0,
        "throughputUsersPerSecond": round(success_users / wall_seconds, 3) if wall_seconds else 0.0,
        "environment": {
            "platform": platform.platform(),
            "python": platform.python_version(),
            "cpuCount": os.cpu_count(),
        },
        "steps": steps,
        "errors": [
            {"user": result.user_index, "error": result.error}
            for result in results
            if not result.ok
        ],
    }


def write_markdown(report: dict, path: Path, label: str) -> None:
    lines = [
        f"# OfferPilot 压测报告（{label}）",
        "",
        f"- 生成时间：{report['generatedAt']}",
        f"- 目标地址：`{report['baseUrl']}`",
        f"- 并发用户：{report['users']}",
        f"- 总耗时：{report['wallSeconds']} 秒",
        f"- 成功/失败：{report['successUsers']} / {report['failedUsers']}",
        f"- 成功率：{report['successRate']}%",
        f"- 吞吐：{report['throughputUsersPerSecond']} 用户/秒",
        f"- 环境：{report['environment']['platform']}，Python {report['environment']['python']}，"
        f"CPU {report['environment']['cpuCount']} 核",
        "",
        "> 本压测默认 `APP_AI_ENABLED=false`，使用确定性 Mock 蓝图与评估，"
        "结果不包含真实 LLM 延迟。",
        "",
        "## 分步骤延迟",
        "",
        "| 步骤 | 次数 | 成功 | P50 (s) | P95 (s) | P99 (s) |",
        "|------|------|------|---------|---------|---------|",
    ]
    for name, step in report["steps"].items():
        lines.append(
            f"| {name} | {step['count']} | {step['success']} | "
            f"{step['p50Seconds']} | {step['p95Seconds']} | {step['p99Seconds']} |"
        )
    lines.extend(["", "## 错误", ""])
    if report["errors"]:
        for error in report["errors"]:
            lines.append(f"- 用户 {error['user']}：{error['error']}")
    else:
        lines.append("- 无")
    lines.extend(["", "## 结论与建议", ""])
    if report["failedUsers"] == 0:
        lines.append("- 完整链路（注册、简历解析、蓝图生成、SSE 答题、报告）在本次并发下全部成功。")
    else:
        lines.append(f"- 本次有 {report['failedUsers']} 个用户失败，错误明细见上表。")
    lines.extend(
        [
            "- 若后续启用真实 LLM，答题与报告阶段延迟会显著上升，应单独压测 AI 调用链路。",
            "- 生产环境建议继续观察 MySQL 连接池、SSE 连接数与 Nginx `proxy_read_timeout`。",
            "",
        ]
    )
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="OfferPilot load test")
    parser.add_argument("--users", type=int, default=20)
    parser.add_argument("--base-url", default="http://localhost:8088")
    parser.add_argument("--output", default="docs/reports")
    parser.add_argument("--run-id", default=None)
    args = parser.parse_args()

    run_id = args.run_id or uuid.uuid4().hex[:10]
    users = [VirtualUser(i, args.base_url, run_id) for i in range(1, args.users + 1)]
    barrier = threading.Barrier(len(users))

    def run_one(user: VirtualUser) -> UserResult:
        barrier.wait()
        return user.run()

    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(users)) as pool:
        results = list(pool.map(run_one, users))
    wall_seconds = time.perf_counter() - started

    report = summarize(results, wall_seconds, args.base_url)
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)
    label = f"{args.users} 并发"
    json_path = output_dir / f"load-test-{args.users}u.json"
    md_path = output_dir / f"load-test-{args.users}u.md"
    json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    write_markdown(report, md_path, label)

    print(json.dumps({
        "users": report["users"],
        "successRate": report["successRate"],
        "wallSeconds": report["wallSeconds"],
        "throughputUsersPerSecond": report["throughputUsersPerSecond"],
        "json": str(json_path),
        "markdown": str(md_path),
    }, ensure_ascii=False, indent=2))
    return 0 if report["failedUsers"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
