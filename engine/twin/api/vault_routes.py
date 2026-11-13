import base64
import binascii
import hmac
import os

from cryptography.exceptions import InvalidTag
from fastapi import APIRouter, Depends, Header, HTTPException, Request, Response
from pydantic import BaseModel, ConfigDict, Field

from ..vault import EncryptedVault, MAX_ENVELOPE_BYTES

router = APIRouter(prefix="/v1/vault")
MAX_BASE64 = 4 * ((MAX_ENVELOPE_BYTES + 2) // 3)


def require_vault_token(request: Request, token: str | None = Header(None, alias="X-Twin-Token")) -> None:
    # Never inherit the legacy API's permissive verifier or public default token.
    expected = os.getenv("TWIN_TOKEN", "")
    if len(expected) < 32:
        raise HTTPException(503, "Vault credentials are not configured")
    local = request.client is not None and request.client.host in {"127.0.0.1", "::1"}
    if request.url.scheme != "https" and not local:
        raise HTTPException(403, "Vault requires HTTPS")
    if token is None or not hmac.compare_digest(token.encode(), expected.encode()):
        raise HTTPException(401, "Vault authentication required")


class BackupEnvelope(BaseModel):
    model_config = ConfigDict(extra="forbid")
    envelope: str = Field(max_length=MAX_BASE64)


@router.post("/backup-phone-state", dependencies=[Depends(require_vault_token)])
async def backup_phone_state(request: Request, response: Response):
    # Limit the body before JSON parsing and never include user input in error responses.
    body = bytearray()
    async for chunk in request.stream():
        if len(body) + len(chunk) > MAX_BASE64 + 64:
            raise HTTPException(413, "Vault payload too large")
        body.extend(chunk)
    try:
        payload = BackupEnvelope.model_validate_json(body)
        envelope = base64.b64decode(payload.envelope, validate=True)
    except (ValueError, binascii.Error):
        raise HTTPException(400, "Invalid recovery envelope") from None
    from starlette.concurrency import run_in_threadpool

    try:
        await run_in_threadpool(EncryptedVault().store, envelope)
    except ValueError:
        raise HTTPException(400, "Invalid recovery envelope") from None
    except OSError:
        raise HTTPException(503, "Vault storage unavailable") from None
    response.headers["Cache-Control"] = "no-store"
    return {"status": "encrypted_and_stored"}


@router.get("/restore-phone-state", dependencies=[Depends(require_vault_token)])
def restore_phone_state(response: Response):
    try:
        envelope = EncryptedVault().restore()
    except (OSError, ValueError, InvalidTag):
        raise HTTPException(409, "Vault cannot be opened") from None
    if envelope is None:
        raise HTTPException(404, "No saved recovery profile")
    response.headers["Cache-Control"] = "no-store"
    return {"envelope": base64.b64encode(envelope).decode("ascii")}


@router.delete("/wipe-all", dependencies=[Depends(require_vault_token)])
def wipe_all(response: Response):
    try:
        EncryptedVault().wipe()
    except OSError:
        raise HTTPException(503, "Vault deletion failed") from None
    response.headers["Cache-Control"] = "no-store"
    return {"status": "deleted"}
