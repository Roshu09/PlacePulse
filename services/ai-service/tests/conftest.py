import io
import time
import uuid

import jwt
import pytest
from fastapi.testclient import TestClient
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

from app.config import Settings
from app.db import make_engine, make_session_factory
from app.embeddings import FakeEmbedder
from app.llm import AnalysisUnavailable, MatchAnalysis
from app.main import create_app
from app.service import Deps

SECRET = "test_secret_test_secret_test_secret_test_secret_1234"

JAVA_JD = "Backend developer. Java, Spring Boot, Kafka, PostgreSQL, Docker, REST APIs, microservices."

JAVA_RESUME = [
    "Built PlacePulse, a campus placement platform with Java, Spring Boot and Kafka microservices.",
    "Designed REST APIs backed by PostgreSQL and containerised every service with Docker.",
    "Skills: Java, Spring Boot, Kafka, PostgreSQL, Docker, Redis, React, TypeScript.",
    "Completed a Master of Computer Applications with a CGPA above eight and led a placement team.",
]

COOKING_RESUME = [
    "Head chef for five years running a busy restaurant kitchen and a team of twelve cooks.",
    "Specialised in pastry, bread baking, plating and menu design for weddings and events.",
    "Skills: knife work, sauces, food safety, inventory control, catering, customer service.",
    "Completed a diploma in hotel management and won two regional baking competitions.",
]


def make_pdf(lines: list[str]) -> bytes:
    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    y = 800
    for line in lines:
        c.drawString(40, y, line[:110])
        y -= 24
    c.save()
    return buf.getvalue()


def token(role: str = "STUDENT", user_id: uuid.UUID | None = None, secret: str = SECRET, alg: str = "HS256") -> str:
    return jwt.encode(
        {"sub": str(user_id or uuid.uuid4()), "email": "x@example.com", "role": role, "exp": int(time.time()) + 3600},
        secret.encode(),
        algorithm=alg,
    )


class FakeAnalyzer:
    def __init__(self):
        self.calls = 0
        self.fail: Exception | None = None

    def analyze(self, drive_text, excerpts):
        self.calls += 1
        if self.fail:
            raise self.fail
        return MatchAnalysis(score=81, summary="Strong backend fit.", matched_skills=["Java", "Kafka"],
                             missing_skills=["Kubernetes"], suggestions=["Add a Kubernetes project."])


class FakeDrives:
    def __init__(self):
        self.drive_data = {"id": "x", "company": "Acme", "roleTitle": "Backend Engineer", "location": "Pune",
                           "description": JAVA_JD}
        self.applicant_rows: list[dict] = []

    def drive(self, token, drive_id):
        return {**self.drive_data, "id": drive_id}

    def applicants(self, token, drive_id):
        return self.applicant_rows


@pytest.fixture()
def env():
    settings = Settings(db_url="sqlite://", jwt_secret=SECRET, groq_api_key="test", sim_low=0.0, sim_high=0.6)
    analyzer, drives = FakeAnalyzer(), FakeDrives()
    deps = Deps(settings, make_session_factory(make_engine("sqlite://")), FakeEmbedder(), analyzer, drives)
    with TestClient(create_app(deps)) as client:
        yield client, deps, analyzer, drives


def auth(tok: str) -> dict:
    return {"Authorization": f"Bearer {tok}"}


def upload(client, tok: str, lines: list[str], name: str = "cv.pdf"):
    return client.post("/api/resume", headers=auth(tok), files={"file": (name, make_pdf(lines), "application/pdf")})
