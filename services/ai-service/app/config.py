import os
from dataclasses import dataclass, field

from sqlalchemy.engine import URL


def _env(name: str, default: str = "") -> str:
    return os.environ.get(name, default)


def _db_url() -> str:
    explicit = _env("AI_DB_URL")
    if explicit:
        return explicit
    user = _env("DB_USER") or _env("POSTGRES_USER", "placepulse")
    password = _env("DB_PASSWORD") or _env("POSTGRES_PASSWORD", "")
    host = _env("DB_HOST", "localhost")
    port = _env("DB_PORT", "5433")  # the port Docker publishes Postgres on, on the host
    # built with URL.create so a password containing @ : / # ? is escaped correctly instead of breaking the URL
    return URL.create("postgresql+psycopg", username=user, password=password, host=host, port=int(port),
                      database="ai_db").render_as_string(hide_password=False)


@dataclass(frozen=True)
class Settings:
    db_url: str = field(default_factory=_db_url)
    # Must be the SAME secret as the Java services.
    jwt_secret: str = field(default_factory=lambda: _env("JWT_SECRET", "dev_only_secret_change_me_dev_only_secret"))
    drive_service_url: str = field(default_factory=lambda: _env("DRIVE_SERVICE_URL", "http://localhost:8082"))
    cors_origins: list[str] = field(
        default_factory=lambda: [o.strip() for o in _env("CORS_ALLOWED_ORIGINS", "http://localhost:5173").split(",") if o.strip()]
    )
    groq_api_key: str = field(default_factory=lambda: _env("GROQ_API_KEY"))
    groq_model: str = field(default_factory=lambda: _env("GROQ_MODEL", "openai/gpt-oss-120b"))
    embedding_model: str = field(default_factory=lambda: _env("EMBEDDING_MODEL", "BAAI/bge-small-en-v1.5"))
    # cosine similarity that maps to a 0% and a 100% fit. A rough calibration for the default model, tune if needed.
    sim_low: float = field(default_factory=lambda: float(_env("SIM_LOW", "0.45")))
    sim_high: float = field(default_factory=lambda: float(_env("SIM_HIGH", "0.80")))
    max_resume_bytes: int = 2 * 1024 * 1024
    top_chunks: int = 6


def get_settings() -> Settings:
    return Settings()
