import pytest

from app.chunking import chunk_resume
from app.embeddings import FakeEmbedder, cosine, similarity_score, top_chunks
from app.llm import AnalysisUnavailable, GroqAnalyzer, parse_analysis
from app.pdf import ResumeUnreadable, extract_text
from app.config import Settings
from tests.conftest import JAVA_RESUME, make_pdf


def test_pdf_text_is_extracted():
    text = extract_text(make_pdf(JAVA_RESUME))
    assert "Spring Boot" in text and "Kafka" in text


def test_non_pdf_and_empty_pdf_are_refused():
    with pytest.raises(ResumeUnreadable, match="not a PDF"):
        extract_text(b"hello world")
    with pytest.raises(ResumeUnreadable, match="No readable text"):
        extract_text(make_pdf(["hi"]))
    with pytest.raises(ResumeUnreadable):
        extract_text(b"%PDF-1.4 this is not really a pdf")


def test_chunks_stay_small_and_keep_every_word():
    text = "\n".join(JAVA_RESUME * 5)
    chunks = chunk_resume(text, max_chars=200)
    assert chunks and all(len(c) <= 200 for c in chunks)
    assert "Kafka" in " ".join(chunks)


def test_a_line_without_punctuation_is_still_cut():
    chunks = chunk_resume("word " * 400, max_chars=100)
    assert all(len(c) <= 100 for c in chunks)


def test_similar_text_scores_higher_than_unrelated_text():
    e = FakeEmbedder()
    jd = e.embed_query("Java Spring Boot Kafka backend")
    chunks = [{"text": t, "embedding": v} for t, v in zip(["Java Spring Boot Kafka services", "baking bread pastry"],
                                                          e.embed_passages(["Java Spring Boot Kafka services", "baking bread pastry"]))]
    best = top_chunks(jd, chunks, 2)
    assert best[0][1].startswith("Java")
    assert cosine(jd, jd) == pytest.approx(1.0)
    assert similarity_score(best, 0.0, 0.6) > similarity_score(best[1:], 0.0, 0.6)
    assert similarity_score([], 0.0, 0.6) == 0


def test_score_is_clamped_between_0_and_100():
    assert similarity_score([(2.0, "a")], 0.1, 0.5) == 100
    assert similarity_score([(-1.0, "a")], 0.1, 0.5) == 0


GOOD = '{"score": 74, "summary": "Good.", "matched_skills": ["Java"], "missing_skills": ["AWS"], "suggestions": ["Add AWS"]}'


def test_json_is_found_inside_chatter_and_fences():
    a = parse_analysis("Sure! ```json\n" + GOOD + "\n``` hope that helps")
    assert a.score == 74 and a.matched_skills == ["Java"]


def test_out_of_range_score_is_clamped_and_bad_shapes_rejected():
    assert parse_analysis('{"score": 400, "summary": "x"}').score == 100
    assert parse_analysis('{"score": -5, "summary": "x", "matched_skills": "oops"}').matched_skills == []
    with pytest.raises(AnalysisUnavailable):
        parse_analysis("no json here")
    with pytest.raises(AnalysisUnavailable):
        parse_analysis('{"summary": "no score"}')


class _Chain:
    def __init__(self, result=None, error=None):
        self.result, self.error, self.seen = result, error, None

    def invoke(self, values):
        self.seen = values
        if self.error:
            raise self.error
        return self.result


def test_analyzer_passes_numbered_excerpts_to_the_chain():
    chain = _Chain(GOOD)
    a = GroqAnalyzer(Settings(groq_api_key="k"), chain=chain).analyze("JD", ["first", "second"])
    assert a.score == 74
    assert "[1] first" in chain.seen["excerpts"] and "[2] second" in chain.seen["excerpts"]


def test_analyzer_turns_any_failure_into_unavailable():
    with pytest.raises(AnalysisUnavailable, match="busy or unreachable"):
        GroqAnalyzer(Settings(groq_api_key="k"), chain=_Chain(error=RuntimeError("429 rate limit"))).analyze("JD", ["x"])
    with pytest.raises(AnalysisUnavailable, match="No Groq key"):
        GroqAnalyzer(Settings(groq_api_key="")).analyze("JD", ["x"])


def test_database_url_survives_a_password_with_special_characters(monkeypatch):
    from sqlalchemy.engine import make_url

    from app.config import _db_url

    monkeypatch.delenv("AI_DB_URL", raising=False)
    monkeypatch.setenv("DB_USER", "placepulse")
    monkeypatch.setenv("DB_PASSWORD", "p@ss:w/ord#1?x")
    monkeypatch.setenv("DB_HOST", "localhost")
    monkeypatch.setenv("DB_PORT", "5433")
    url = make_url(_db_url())
    assert url.host == "localhost" and url.port == 5433 and url.database == "ai_db"
    assert url.username == "placepulse" and url.password == "p@ss:w/ord#1?x"
