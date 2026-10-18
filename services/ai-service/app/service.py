import hashlib
import logging
from dataclasses import dataclass

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from .auth import CurrentUser
from .chunking import chunk_resume
from .clients import DriveClient
from .config import Settings
from .db import Match, Resume, utcnow
from .embeddings import Embedder, similarity_score, top_chunks
from .llm import Analyzer, AnalysisUnavailable
from .pdf import ResumeUnreadable, extract_text

log = logging.getLogger("ai.service")


@dataclass
class Deps:
    settings: Settings
    sessions: object  # a sessionmaker
    embedder: Embedder
    analyzer: Analyzer
    drives: DriveClient


def sha(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def drive_text(drive: dict) -> str:
    """The job description as one block of text: title first, then the description the TPR wrote."""
    parts = [f"{drive.get('company', '')}: {drive.get('roleTitle', '')}"]
    if drive.get("location"):
        parts.append(f"Location: {drive['location']}")
    if drive.get("description"):
        parts.append(str(drive["description"]))
    return "\n".join(parts).strip()


# ---- resume ------------------------------------------------------------------------------

def save_resume(deps: Deps, session: Session, student_id: str, filename: str, data: bytes) -> Resume:
    try:
        text = extract_text(data)
    except ResumeUnreadable as e:
        raise HTTPException(422, str(e)) from None
    pieces = chunk_resume(text)
    if not pieces:
        raise HTTPException(422, "No readable text found in that PDF.")
    vectors = deps.embedder.embed_passages(pieces)
    chunks = [{"text": t, "embedding": v} for t, v in zip(pieces, vectors)]
    resume = session.get(Resume, student_id)
    if resume is None:
        resume = Resume(student_id=student_id)
        session.add(resume)
    resume.filename = filename[:255]
    resume.text = text
    resume.text_hash = sha(text)
    resume.chunks = chunks
    resume.uploaded_at = utcnow()
    session.commit()
    return resume


def resume_info(resume: Resume | None) -> dict:
    if resume is None:
        return {"uploaded": False}
    return {
        "uploaded": True,
        "filename": resume.filename,
        "uploadedAt": resume.uploaded_at.isoformat(),
        "characters": len(resume.text),
        "sections": len(resume.chunks),
    }


# ---- matching -----------------------------------------------------------------------------

def _best(deps: Deps, resume: Resume, text: str):
    query = deps.embedder.embed_query(text[:2000])
    return query, top_chunks(query, resume.chunks, deps.settings.top_chunks)


def match_for_student(deps: Deps, session: Session, user: CurrentUser, drive_id: str) -> dict:
    resume = session.get(Resume, str(user.id))
    if resume is None:
        raise HTTPException(409, "Upload your resume first.")
    drive = deps.drives.drive(user.token, drive_id)
    text = drive_text(drive)
    dhash = sha(text)

    cached = session.scalar(select(Match).where(Match.student_id == str(user.id), Match.drive_id == drive_id))
    if cached and cached.resume_hash == resume.text_hash and cached.drive_hash == dhash:
        return {**cached.result, "cached": True}

    _, best = _best(deps, resume, text)
    sim = similarity_score(best, deps.settings.sim_low, deps.settings.sim_high)
    thin = not drive.get("description")
    try:
        a = deps.analyzer.analyze(text[:3000], [t[:700] for _, t in best])
        result = {
            "driveId": drive_id, "score": a.score, "similarity": sim, "summary": a.summary,
            "matchedSkills": a.matched_skills, "missingSkills": a.missing_skills, "suggestions": a.suggestions,
            "aiGenerated": True, "notice": None, "cached": False,
        }
        if cached is None:
            cached = Match(student_id=str(user.id), drive_id=drive_id)
            session.add(cached)
        cached.resume_hash, cached.drive_hash, cached.result, cached.created_at = resume.text_hash, dhash, result, utcnow()
        session.commit()
    except AnalysisUnavailable as e:
        log.info("Falling back to similarity only: %s", e)
        result = {
            "driveId": drive_id, "score": sim, "similarity": sim,
            "summary": "AI feedback is not available right now, so this score only compares your resume text with the job text.",
            "matchedSkills": [], "missingSkills": [], "suggestions": [],
            "aiGenerated": False, "notice": str(e), "cached": False,
        }
    if thin:
        result["notice"] = (result.get("notice") or "") + " This drive has no description yet, so the match is based on the role title only."
        result["notice"] = result["notice"].strip()
    return result


def rank_applicants(deps: Deps, session: Session, user: CurrentUser, drive_id: str) -> list[dict]:
    drive = deps.drives.drive(user.token, drive_id)
    applicants = deps.drives.applicants(user.token, drive_id)
    text = drive_text(drive)
    dhash = sha(text)
    ids = [str(a["studentId"]) for a in applicants if a.get("studentId")]
    resumes = {r.student_id: r for r in session.scalars(select(Resume).where(Resume.student_id.in_(ids)))} if ids else {}
    matches = {m.student_id: m for m in session.scalars(select(Match).where(Match.drive_id == drive_id))}
    query = deps.embedder.embed_query(text[:2000]) if resumes else None

    rows = []
    for a in applicants:
        sid = str(a.get("studentId", ""))
        row = {"studentId": sid, "studentName": a.get("studentName"), "stage": a.get("stage"),
               "hasResume": sid in resumes, "similarity": None, "aiScore": None, "score": None}
        resume = resumes.get(sid)
        if resume is not None:
            best = top_chunks(query, resume.chunks, deps.settings.top_chunks)
            row["similarity"] = similarity_score(best, deps.settings.sim_low, deps.settings.sim_high)
            m = matches.get(sid)
            if m and m.resume_hash == resume.text_hash and m.drive_hash == dhash:
                row["aiScore"] = m.result.get("score")
            row["score"] = row["aiScore"] if row["aiScore"] is not None else row["similarity"]
        rows.append(row)
    rows.sort(key=lambda r: (r["score"] is None, -(r["score"] or 0), r["studentName"] or ""))
    return rows
