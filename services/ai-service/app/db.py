import datetime as dt

from sqlalchemy import JSON, DateTime, Integer, String, Text, UniqueConstraint, create_engine
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker
from sqlalchemy.pool import StaticPool


def utcnow() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


class Base(DeclarativeBase):
    pass


class Resume(Base):
    """One resume per student. Re-uploading replaces it."""

    __tablename__ = "resumes"

    student_id: Mapped[str] = mapped_column(String(36), primary_key=True)
    filename: Mapped[str] = mapped_column(String(255))
    text: Mapped[str] = mapped_column(Text)
    text_hash: Mapped[str] = mapped_column(String(64))
    # [{"text": "...", "embedding": [floats]}]: the resume cut into pieces, each embedded
    chunks: Mapped[list] = mapped_column(JSON)
    uploaded_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class Match(Base):
    """A finished AI match, reused until the resume or the drive text changes."""

    __tablename__ = "matches"
    __table_args__ = (UniqueConstraint("student_id", "drive_id"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    student_id: Mapped[str] = mapped_column(String(36), index=True)
    drive_id: Mapped[str] = mapped_column(String(36), index=True)
    resume_hash: Mapped[str] = mapped_column(String(64))
    drive_hash: Mapped[str] = mapped_column(String(64))
    result: Mapped[dict] = mapped_column(JSON)
    created_at: Mapped[dt.datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


def make_engine(url: str):
    if url.startswith("sqlite"):
        # one shared in-memory database for tests
        return create_engine(url, connect_args={"check_same_thread": False}, poolclass=StaticPool)
    return create_engine(url, pool_pre_ping=True)


def make_session_factory(engine):
    Base.metadata.create_all(engine)
    return sessionmaker(engine, expire_on_commit=False)
