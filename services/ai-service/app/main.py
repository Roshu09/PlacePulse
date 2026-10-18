import logging
import uuid
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, File, HTTPException, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware

from . import service
from .auth import CurrentUser, require_student, require_tpr
from .clients import HttpDriveClient
from .config import Settings, get_settings
from .db import Resume, make_engine, make_session_factory
from .embeddings import LocalEmbedder
from .llm import GroqAnalyzer
from .service import Deps

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")


def default_deps(settings: Settings) -> Deps:
    return Deps(
        settings=settings,
        sessions=make_session_factory(make_engine(settings.db_url)),
        embedder=LocalEmbedder(settings.embedding_model),
        analyzer=GroqAnalyzer(settings),
        drives=HttpDriveClient(settings.drive_service_url),
    )


def create_app(deps: Deps | None = None) -> FastAPI:
    settings = deps.settings if deps else get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.deps = deps or default_deps(settings)
        if not settings.groq_api_key:
            logging.getLogger("ai").warning("GROQ_API_KEY is not set: matches will use text similarity only.")
        yield

    app = FastAPI(title="PlacePulse AI service", lifespan=lifespan)
    app.dependency_overrides[get_settings] = lambda: settings
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_methods=["GET", "POST", "DELETE", "OPTIONS"],
        allow_headers=["Authorization", "Content-Type"],
    )

    def get_deps(request: Request) -> Deps:
        return request.app.state.deps

    @app.get("/health")
    def health():
        return {"status": "ok"}

    # ---- resume (students) ----
    @app.get("/api/resume")
    def my_resume(user: CurrentUser = Depends(require_student), d: Deps = Depends(get_deps)):
        with d.sessions() as session:
            return service.resume_info(session.get(Resume, str(user.id)))

    @app.post("/api/resume")
    def upload_resume(file: UploadFile = File(...), user: CurrentUser = Depends(require_student), d: Deps = Depends(get_deps)):
        data = file.file.read(d.settings.max_resume_bytes + 1)
        if len(data) > d.settings.max_resume_bytes:
            raise HTTPException(413, "That PDF is bigger than 2 MB. Export a smaller one.")
        with d.sessions() as session:
            resume = service.save_resume(d, session, str(user.id), file.filename or "resume.pdf", data)
            return service.resume_info(resume)

    @app.delete("/api/resume", status_code=204)
    def delete_resume(user: CurrentUser = Depends(require_student), d: Deps = Depends(get_deps)):
        with d.sessions() as session:
            resume = session.get(Resume, str(user.id))
            if resume is not None:
                session.delete(resume)
                session.commit()

    # ---- matching ----
    @app.post("/api/matches/drives/{drive_id}")
    def match_drive(drive_id: uuid.UUID, user: CurrentUser = Depends(require_student), d: Deps = Depends(get_deps)):
        with d.sessions() as session:
            return service.match_for_student(d, session, user, str(drive_id))

    @app.get("/api/matches/drives/{drive_id}/applicants")
    def rank(drive_id: uuid.UUID, user: CurrentUser = Depends(require_tpr), d: Deps = Depends(get_deps)):
        with d.sessions() as session:
            return service.rank_applicants(d, session, user, str(drive_id))

    return app


app = create_app()
