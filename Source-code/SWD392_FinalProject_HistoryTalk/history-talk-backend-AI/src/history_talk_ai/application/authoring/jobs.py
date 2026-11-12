"""
Background jobs for generations that take longer than an HTTP request should wait.

Jobs live in memory: there is a single AI service instance and a lost job only means the author asks again.
"""

import asyncio
import logging
import time
import uuid
from dataclasses import dataclass, field
from typing import Awaitable, Callable

logger = logging.getLogger(__name__)

JOB_TTL_SECONDS = 30 * 60

STAGES = ("retrieving", "generating", "validating", "repairing", "done")


class AuthoringInputError(Exception):
    """The request cannot be drafted, e.g. the context has no documents. Reported as 422."""


@dataclass
class Job:
    id: str
    status: str = "queued"
    stage: str = "retrieving"
    progress: int = 0
    result: dict | None = None
    error: str | None = None
    created_at: float = field(default_factory=time.monotonic)
    task: asyncio.Task | None = None

    def to_dict(self) -> dict:
        body = {"jobId": self.id, "status": self.status, "stage": self.stage, "progress": self.progress}
        if self.result is not None:
            body["result"] = self.result
        if self.error is not None:
            body["error"] = self.error
        return body


class Progress:
    """Lets a generation report its stage; a synchronous request passes one that only discards it."""

    def __init__(self, job: Job | None = None):
        self._job = job

    def update(self, stage: str, progress: int) -> None:
        if self._job is not None:
            self._job.stage = stage
            self._job.progress = progress


_jobs: dict[str, Job] = {}


def _purge_expired() -> None:
    now = time.monotonic()
    for job_id in [jid for jid, job in _jobs.items() if now - job.created_at > JOB_TTL_SECONDS]:
        job = _jobs.pop(job_id)
        if job.task is not None and not job.task.done():
            job.task.cancel()


def start_job(work: Callable[[Progress], Awaitable[dict]]) -> Job:
    _purge_expired()
    job = Job(id=uuid.uuid4().hex)
    _jobs[job.id] = job

    async def run() -> None:
        job.status = "running"
        try:
            job.result = await work(Progress(job))
            job.status = "succeeded"
            job.stage = "done"
            job.progress = 100
        except asyncio.CancelledError:
            job.status = "cancelled"
            raise
        except AuthoringInputError as exc:
            job.status = "failed"
            job.error = str(exc)
        except Exception as exc:
            # Raw errors can contain internal URLs, so the author only gets a generic message.
            logger.error(f"Authoring job {job.id} failed: {exc}")
            job.status = "failed"
            job.error = "Dịch vụ AI đang gặp sự cố. Vui lòng thử lại sau."

    job.task = asyncio.create_task(run())
    return job


def get_job(job_id: str) -> Job | None:
    _purge_expired()
    return _jobs.get(job_id)


def cancel_job(job_id: str) -> bool:
    job = _jobs.get(job_id)
    if job is None:
        return False
    if job.task is not None and not job.task.done():
        job.task.cancel()
    job.status = "cancelled"
    return True
