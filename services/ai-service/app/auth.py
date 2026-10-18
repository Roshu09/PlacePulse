import uuid
from dataclasses import dataclass

import jwt
from fastapi import Depends, Header, HTTPException

from .config import Settings, get_settings


@dataclass(frozen=True)
class CurrentUser:
    id: uuid.UUID
    role: str
    token: str  # kept so we can call drive-service as this same user


def current_user(authorization: str | None = Header(default=None), settings: Settings = Depends(get_settings)) -> CurrentUser:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Sign in to continue.")
    token = authorization[7:]
    try:
        # the Java services pick HS256/384/512 by secret length, so accept all three
        claims = jwt.decode(token, settings.jwt_secret.encode(), algorithms=["HS256", "HS384", "HS512"])
        return CurrentUser(id=uuid.UUID(claims["sub"]), role=claims["role"], token=token)
    except (jwt.PyJWTError, KeyError, ValueError):
        raise HTTPException(401, "Your session has expired. Sign in again.") from None


def require_student(user: CurrentUser = Depends(current_user)) -> CurrentUser:
    if user.role != "STUDENT":
        raise HTTPException(403, "Only students can do this.")
    return user


def require_tpr(user: CurrentUser = Depends(current_user)) -> CurrentUser:
    if user.role != "TPR":
        raise HTTPException(403, "Only the placement team can do this.")
    return user
