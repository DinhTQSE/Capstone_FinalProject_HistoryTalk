"""Lightweight Prometheus metrics exporter for HistoryTalk AI Service."""

import threading
import time
from collections import defaultdict
from typing import Dict, Tuple

from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware

BUCKETS = (0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0)


class _MetricsStore:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.requests_total: Dict[Tuple[str, str, str], int] = defaultdict(int)
        self.duration_sum: Dict[Tuple[str, str], float] = defaultdict(float)
        self.duration_count: Dict[Tuple[str, str], int] = defaultdict(int)
        self.duration_buckets: Dict[Tuple[str, str, str], int] = defaultdict(int)

    def observe(self, method: str, path: str, status: int, duration: float) -> None:
        status_str = str(status)
        with self._lock:
            self.requests_total[(method, path, status_str)] += 1
            self.duration_sum[(method, path)] += duration
            self.duration_count[(method, path)] += 1
            for b in BUCKETS:
                if duration <= b:
                    self.duration_buckets[(method, path, str(b))] += 1
            self.duration_buckets[(method, path, "+Inf")] += 1

    def render_prometheus(self) -> str:
        lines = [
            "# HELP ai_up Whether the HistoryTalk Python AI service is up (1 = up).",
            "# TYPE ai_up gauge",
            "ai_up 1",
            "# HELP ai_http_requests_total Total number of HTTP requests processed by AI service.",
            "# TYPE ai_http_requests_total counter",
        ]
        with self._lock:
            for (method, path, status), count in sorted(self.requests_total.items()):
                lines.append(
                    f'ai_http_requests_total{{method="{method}",path="{path}",status="{status}"}} {count}'
                )

            lines.append(
                "# HELP ai_http_request_duration_seconds HTTP request latency histogram in seconds."
            )
            lines.append("# TYPE ai_http_request_duration_seconds histogram")
            seen_keys = sorted(self.duration_count.keys())
            for method, path in seen_keys:
                for b in BUCKETS:
                    b_str = str(b)
                    val = self.duration_buckets.get((method, path, b_str), 0)
                    lines.append(
                        f'ai_http_request_duration_seconds_bucket{{method="{method}",path="{path}",le="{b_str}"}} {val}'
                    )
                inf_val = self.duration_buckets.get((method, path, "+Inf"), 0)
                lines.append(
                    f'ai_http_request_duration_seconds_bucket{{method="{method}",path="{path}",le="+Inf"}} {inf_val}'
                )
                s_val = self.duration_sum.get((method, path), 0.0)
                c_val = self.duration_count.get((method, path), 0)
                lines.append(
                    f'ai_http_request_duration_seconds_sum{{method="{method}",path="{path}"}} {s_val:.6f}'
                )
                lines.append(
                    f'ai_http_request_duration_seconds_count{{method="{method}",path="{path}"}} {c_val}'
                )

        return "\n".join(lines) + "\n"


metrics_store = _MetricsStore()


def _normalize_path(path: str) -> str:
    if path.startswith("/v1/ai/documents/") and path != "/v1/ai/documents/process":
        return "/v1/ai/documents/{doc_id}"
    return path


class PrometheusMetricsMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        if request.url.path == "/metrics":
            return await call_next(request)
        start = time.perf_counter()
        status_code = 500
        try:
            response = await call_next(request)
            status_code = response.status_code
            return response
        finally:
            duration = max(0.0, time.perf_counter() - start)
            norm_path = _normalize_path(request.url.path)
            metrics_store.observe(request.method, norm_path, status_code, duration)


def metrics_endpoint() -> Response:
    return Response(
        content=metrics_store.render_prometheus(),
        media_type="text/plain; version=0.0.4; charset=utf-8",
    )
