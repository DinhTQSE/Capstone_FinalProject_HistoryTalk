"""Drafting story scripts and battle-map plans for content authors. Only the Java backend calls these."""

import logging
from typing import Awaitable, Callable

from fastapi import APIRouter, HTTPException, status
from fastapi.responses import JSONResponse, Response

from history_talk_ai.application.authoring import jobs
from history_talk_ai.application.authoring.battle_map import locate_anchors, plan_battle_map
from history_talk_ai.application.authoring.story import generate_story
from history_talk_ai.application.chat import service as llm_service
from history_talk_ai.dataaccess.java_backend import client as java_client
from history_talk_ai.dataaccess.java_backend.client import JavaBackendError
from history_talk_ai.presentation.authoring.schemas import (
    BattlePlanRequest,
    LocateAnchorsRequest,
    StoryGenerateRequest,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/v1/ai", tags=["AI Authoring"])


async def _context_and_characters(body) -> tuple[dict, list[dict]]:
    """Uses what the Java backend sent, so drafts of unpublished contexts work too."""
    try:
        context = body.contextData or await java_client.get_historical_context(body.contextId)
    except JavaBackendError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Không tìm thấy bối cảnh.") from exc
    return context.model_dump(), [c.model_dump() for c in body.characterData]


def _check_story_inputs(body: StoryGenerateRequest, characters: list[dict]) -> None:
    missing = None
    if body.mode in ("continue", "quiz") and not body.previousSteps:
        missing = "previousSteps"
    elif body.mode == "continue" and body.count is None:
        missing = "count"
    elif body.mode == "rewrite" and (not body.step or not body.instruction):
        missing = "step và instruction"
    if missing:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"Mode {body.mode} cần {missing}.")
    if body.narratorId and body.narratorId not in {c["characterId"] for c in characters}:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Không tìm thấy nhân vật kể chính.")


async def _run(run_async: bool, work: Callable[[jobs.Progress], Awaitable[dict]]):
    """Long drafts become a job the editor polls; short ones answer straight away."""
    if run_async:
        job = jobs.start_job(work)
        return JSONResponse(status_code=status.HTTP_202_ACCEPTED, content={"success": True, "data": {"jobId": job.id}})
    try:
        return {"success": True, "data": await work(jobs.Progress())}
    except jobs.AuthoringInputError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))
    except llm_service.RagUnavailableError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exc))
    except Exception as exc:
        logger.error(f"Authoring request failed: {exc}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Dịch vụ AI đang gặp sự cố. Vui lòng thử lại sau.",
        )


@router.post("/story/generate", summary="Draft a story script, or continue, rewrite or quiz part of one")
async def story_generate(body: StoryGenerateRequest):
    context, characters = await _context_and_characters(body)
    _check_story_inputs(body, characters)

    async def work(progress: jobs.Progress) -> dict:
        return await generate_story(
            mode=body.mode,
            context=context,
            characters=characters,
            narrator_id=body.narratorId,
            backgrounds=[b.model_dump() for b in body.backgrounds],
            options=body.options.model_dump(),
            progress=progress,
            previous_steps=body.previousSteps,
            step=body.step,
            instruction=body.instruction,
            count=body.count,
        )

    return await _run(body.run_async, work)


@router.post("/battle-map/plan", summary="Plan factions, landmarks, phases and symbols of a battle map")
async def battle_map_plan(body: BattlePlanRequest):
    context, characters = await _context_and_characters(body)

    async def work(progress: jobs.Progress) -> dict:
        return await plan_battle_map(
            context=context,
            characters=characters,
            options=body.options.model_dump(),
            existing_factions=[f.model_dump() for f in body.existingFactions],
            existing_anchors=[a.model_dump() for a in body.existingAnchors],
            story_summary=body.storySummary,
            progress=progress,
        )

    return await _run(body.run_async, work)


@router.post("/battle-map/locate-anchors", summary="Suggest where landmarks are on a battle-map image")
async def battle_map_locate_anchors(body: LocateAnchorsRequest):
    try:
        data = await locate_anchors(body.imageUrl, body.imageBase64, [a.model_dump() for a in body.anchors])
    except jobs.AuthoringInputError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))
    except Exception as exc:
        logger.error(f"Locating landmarks failed: {exc}")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Chưa đoán được vị trí điểm mốc. Vui lòng tự đặt trên ảnh.",
        )
    return {"success": True, "data": data}


@router.get("/jobs/{job_id}", summary="Status of a long draft")
async def get_job(job_id: str):
    job = jobs.get_job(job_id)
    if job is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Không tìm thấy job hoặc job đã hết hạn.")
    return {"success": True, "data": job.to_dict()}


@router.delete("/jobs/{job_id}", status_code=status.HTTP_204_NO_CONTENT, summary="Cancel a long draft")
async def cancel_job(job_id: str):
    if not jobs.cancel_job(job_id):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Không tìm thấy job hoặc job đã hết hạn.")
    return Response(status_code=status.HTTP_204_NO_CONTENT)
