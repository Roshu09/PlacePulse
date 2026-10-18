import uuid

from app.llm import AnalysisUnavailable
from tests.conftest import COOKING_RESUME, JAVA_RESUME, auth, make_pdf, token, upload

DRIVE = str(uuid.uuid4())


def test_requests_without_a_valid_token_are_refused(env):
    client, *_ = env
    assert client.get("/api/resume").status_code == 401
    assert client.get("/api/resume", headers=auth("garbage")).status_code == 401
    assert client.get("/api/resume", headers=auth(token(secret="another_secret_another_secret_another_secret_12"))).status_code == 401


def test_tokens_signed_with_any_hs_algorithm_work(env):
    client, *_ = env
    for alg in ("HS256", "HS384", "HS512"):
        assert client.get("/api/resume", headers=auth(token(alg=alg))).status_code == 200


def test_roles_are_enforced(env):
    client, *_ = env
    assert client.get("/api/resume", headers=auth(token("TPR"))).status_code == 403
    assert client.post(f"/api/matches/drives/{DRIVE}", headers=auth(token("TPR"))).status_code == 403
    assert client.get(f"/api/matches/drives/{DRIVE}/applicants", headers=auth(token("STUDENT"))).status_code == 403


def test_upload_read_back_and_delete(env):
    client, *_ = env
    tok = token()
    assert client.get("/api/resume", headers=auth(tok)).json() == {"uploaded": False}
    r = upload(client, tok, JAVA_RESUME)
    assert r.status_code == 200 and r.json()["uploaded"] and r.json()["sections"] >= 1
    assert client.get("/api/resume", headers=auth(tok)).json()["filename"] == "cv.pdf"
    assert client.delete("/api/resume", headers=auth(tok)).status_code == 204
    assert client.get("/api/resume", headers=auth(tok)).json() == {"uploaded": False}


def test_bad_uploads_are_rejected_with_a_reason(env):
    client, *_ = env
    tok = token()
    r = client.post("/api/resume", headers=auth(tok), files={"file": ("a.pdf", b"not a pdf", "application/pdf")})
    assert r.status_code == 422 and "not a PDF" in r.json()["detail"]
    big = b"%PDF" + b"0" * (2 * 1024 * 1024 + 10)
    assert client.post("/api/resume", headers=auth(tok), files={"file": ("a.pdf", big, "application/pdf")}).status_code == 413


def test_matching_needs_a_resume(env):
    client, *_ = env
    r = client.post(f"/api/matches/drives/{DRIVE}", headers=auth(token()))
    assert r.status_code == 409 and "Upload your resume" in r.json()["detail"]


def test_match_returns_the_ai_analysis_and_is_cached(env):
    client, deps, analyzer, _ = env
    tok = token()
    upload(client, tok, JAVA_RESUME)

    first = client.post(f"/api/matches/drives/{DRIVE}", headers=auth(tok)).json()
    assert first["aiGenerated"] and first["score"] == 81
    assert first["matchedSkills"] == ["Java", "Kafka"] and first["missingSkills"] == ["Kubernetes"]
    assert 0 <= first["similarity"] <= 100 and first["cached"] is False

    second = client.post(f"/api/matches/drives/{DRIVE}", headers=auth(tok)).json()
    assert second["cached"] is True and analyzer.calls == 1


def test_a_new_resume_or_a_changed_drive_invalidates_the_cache(env):
    client, deps, analyzer, drives = env
    tok = token()
    upload(client, tok, JAVA_RESUME)
    client.post(f"/api/matches/drives/{DRIVE}", headers=auth(tok))

    drives.drive_data["description"] = "Now we need Go and Kubernetes too."
    client.post(f"/api/matches/drives/{DRIVE}", headers=auth(tok))
    assert analyzer.calls == 2

    upload(client, tok, COOKING_RESUME)
    client.post(f"/api/matches/drives/{DRIVE}", headers=auth(tok))
    assert analyzer.calls == 3


def test_when_the_ai_is_down_the_match_falls_back_to_similarity(env):
    client, _, analyzer, _ = env
    analyzer.fail = AnalysisUnavailable("The AI service is busy or unreachable (RateLimitError).")
    tok = token()
    upload(client, tok, JAVA_RESUME)

    r = client.post(f"/api/matches/drives/{DRIVE}", headers=auth(tok))
    body = r.json()
    assert r.status_code == 200 and body["aiGenerated"] is False
    assert body["score"] == body["similarity"] and "busy or unreachable" in body["notice"]

    # the failed attempt is not cached: once the AI is back, the next click gets the real thing
    analyzer.fail = None
    assert client.post(f"/api/matches/drives/{DRIVE}", headers=auth(tok)).json()["aiGenerated"] is True


def test_a_drive_without_a_description_says_so(env):
    client, _, _, drives = env
    drives.drive_data["description"] = None
    tok = token()
    upload(client, tok, JAVA_RESUME)
    assert "no description" in client.post(f"/api/matches/drives/{DRIVE}", headers=auth(tok)).json()["notice"]


def test_a_bad_drive_id_is_refused(env):
    client, *_ = env
    assert client.post("/api/matches/drives/not-a-uuid", headers=auth(token())).status_code == 422


def test_tpr_sees_applicants_ranked_by_fit(env):
    client, _, analyzer, drives = env
    java_id, chef_id, none_id = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()
    upload(client, token(user_id=java_id), JAVA_RESUME)
    upload(client, token(user_id=chef_id), COOKING_RESUME)
    drives.applicant_rows = [
        {"studentId": str(none_id), "studentName": "No Resume", "stage": "APPLIED"},
        {"studentId": str(chef_id), "studentName": "Chef", "stage": "APPLIED"},
        {"studentId": str(java_id), "studentName": "Java Dev", "stage": "SHORTLISTED"},
    ]

    rows = client.get(f"/api/matches/drives/{DRIVE}/applicants", headers=auth(token("TPR"))).json()
    assert [r["studentName"] for r in rows] == ["Java Dev", "Chef", "No Resume"]
    assert rows[0]["score"] > rows[1]["score"]
    assert rows[2]["hasResume"] is False and rows[2]["score"] is None
    assert rows[0]["aiScore"] is None and analyzer.calls == 0  # ranking never spends the AI quota


def test_tpr_ranking_shows_the_ai_score_once_a_student_ran_it(env):
    client, _, _, drives = env
    sid = uuid.uuid4()
    tok = token(user_id=sid)
    upload(client, tok, JAVA_RESUME)
    drives.applicant_rows = [{"studentId": str(sid), "studentName": "Java Dev", "stage": "APPLIED"}]
    client.post(f"/api/matches/drives/{DRIVE}", headers=auth(tok))

    row = client.get(f"/api/matches/drives/{DRIVE}/applicants", headers=auth(token("TPR"))).json()[0]
    assert row["aiScore"] == 81 and row["score"] == 81


def test_ranking_with_no_applicants_is_empty(env):
    client, *_ = env
    assert client.get(f"/api/matches/drives/{DRIVE}/applicants", headers=auth(token("TPR"))).json() == []
