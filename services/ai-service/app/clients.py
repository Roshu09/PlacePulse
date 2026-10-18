from typing import Protocol

import httpx
from fastapi import HTTPException


class DriveClient(Protocol):
    def drive(self, token: str, drive_id: str) -> dict: ...

    def applicants(self, token: str, drive_id: str) -> list[dict]: ...


class HttpDriveClient:
    """Asks drive-service, using the caller's own token, so drive-service decides what that user may see."""

    def __init__(self, base_url: str):
        self._base = base_url.rstrip("/")

    def _get(self, token: str, path: str):
        try:
            r = httpx.get(f"{self._base}{path}", headers={"Authorization": f"Bearer {token}"}, timeout=10)
        except httpx.HTTPError:
            raise HTTPException(502, "Could not reach the drive service. Try again in a moment.") from None
        if r.status_code == 404:
            raise HTTPException(404, "Drive not found.")
        if r.status_code in (401, 403):
            raise HTTPException(r.status_code, "You are not allowed to see this drive.")
        if r.status_code >= 400:
            raise HTTPException(502, "The drive service could not answer. Try again in a moment.")
        return r.json()

    def drive(self, token: str, drive_id: str) -> dict:
        return self._get(token, f"/api/drives/{drive_id}")

    def applicants(self, token: str, drive_id: str) -> list[dict]:
        return self._get(token, f"/api/drives/{drive_id}/applications")
