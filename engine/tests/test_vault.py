import base64
import json
from hashlib import pbkdf2_hmac
from secrets import token_bytes

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from fastapi import FastAPI
from fastapi.testclient import TestClient

from engine.twin.api.vault_routes import router


def test_encrypted_vault_lifecycle(tmp_path, monkeypatch):
    monkeypatch.setenv("HUNCH_VAULT_DIR", str(tmp_path))
    monkeypatch.setenv("TWIN_TOKEN", "test-only-credentials-" * 3)
    app = FastAPI()
    app.include_router(router)
    client = TestClient(app, base_url="https://testserver")
    headers = {"X-Twin-Token": "test-only-credentials-" * 3}
    profile = json.dumps({"vipPackages": ["com.example.secret"]}).encode()
    salt, nonce = token_bytes(16), token_bytes(12)
    key = pbkdf2_hmac("sha256", b"test recovery passphrase", salt, 210000, 32)
    envelope = b"HNCHV1" + salt + nonce + AESGCM(key).encrypt(nonce, profile, None)
    body = {"envelope": base64.b64encode(envelope).decode()}
    assert client.post("/v1/vault/backup-phone-state", json=body).status_code == 401
    assert client.post("/v1/vault/backup-phone-state", json=body, headers=headers).status_code == 200
    path = tmp_path / "vault_snapshot.enc"
    assert b"com.example.secret" not in path.read_bytes()
    response = client.get("/v1/vault/restore-phone-state", headers=headers)
    assert response.headers["cache-control"] == "no-store"
    assert base64.b64decode(response.json()["envelope"]) == envelope
    encrypted = path.read_bytes()
    path.write_bytes(encrypted[:-1] + bytes([encrypted[-1] ^ 1]))
    assert client.get("/v1/vault/restore-phone-state", headers=headers).status_code == 409
    assert client.delete("/v1/vault/wipe-all", headers=headers).status_code == 200
    assert not list(tmp_path.iterdir())
    assert client.get("/v1/vault/restore-phone-state", headers=headers).status_code == 404
