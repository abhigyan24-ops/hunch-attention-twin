import ctypes
import os
import secrets
import sys
import tempfile
import threading
from ctypes import wintypes
from pathlib import Path

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC = b"HNCHV1"
LOCAL_MAGIC = b"HNCHL1"
MAX_ENVELOPE_BYTES = 256 * 1024
_LOCK = threading.RLock()


class _Blob(ctypes.Structure):
    _fields_ = [("size", wintypes.DWORD), ("data", ctypes.POINTER(ctypes.c_ubyte))]


def _dpapi(data: bytes, *, protect: bool) -> bytes:
    source_buffer = (ctypes.c_ubyte * len(data)).from_buffer_copy(data)
    source = _Blob(len(data), source_buffer)
    result = _Blob()
    crypt32 = ctypes.WinDLL("crypt32", use_last_error=True)
    operation = crypt32.CryptProtectData if protect else crypt32.CryptUnprotectData
    operation.argtypes = [
        ctypes.POINTER(_Blob), wintypes.LPCWSTR if protect else ctypes.POINTER(wintypes.LPWSTR),
        ctypes.POINTER(_Blob), ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD,
        ctypes.POINTER(_Blob),
    ]
    operation.restype = wintypes.BOOL
    if not operation(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(result)):
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        return ctypes.string_at(result.data, result.size)
    finally:
        local_free = ctypes.WinDLL("kernel32").LocalFree
        local_free.argtypes = [ctypes.c_void_p]
        local_free.restype = ctypes.c_void_p
        ctypes.memset(result.data, 0, result.size)
        local_free(result.data)


def _atomic_write(path: Path, data: bytes) -> None:
    fd, name = tempfile.mkstemp(prefix=".vault-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(name, path)
    finally:
        Path(name).unlink(missing_ok=True)


class EncryptedVault:
    """Stores one opaque phone envelope under a separate local AES-GCM key."""

    def __init__(self, directory: Path | None = None):
        self.directory = directory or Path(os.environ.get("HUNCH_VAULT_DIR") or Path.home() / ".hunch")
        self.snapshot = self.directory / "vault_snapshot.enc"
        self.windows = sys.platform == "win32"
        self.key_path = self.directory / ("master.key.dpapi" if self.windows else "master.key")

    def _key(self, *, create: bool = False) -> bytes:
        if self.key_path.exists():
            if not self.windows and self.key_path.stat().st_mode & 0o077:
                raise PermissionError("Vault key permissions must be owner-only")
            encoded = self.key_path.read_bytes()
            key = _dpapi(encoded, protect=False) if self.windows else encoded
        elif create:
            self.directory.mkdir(parents=True, exist_ok=True, mode=0o700)
            key = AESGCM.generate_key(bit_length=256)
            _atomic_write(self.key_path, _dpapi(key, protect=True) if self.windows else key)
        else:
            raise FileNotFoundError("Vault key is unavailable")
        if len(key) != 32:
            raise ValueError("Invalid vault key")
        return key

    def store(self, envelope: bytes) -> None:
        if not (51 <= len(envelope) <= MAX_ENVELOPE_BYTES and envelope.startswith(MAGIC)):
            raise ValueError("Invalid recovery envelope")
        with _LOCK:
            key = self._key(create=not self.snapshot.exists())
            nonce = secrets.token_bytes(12)
            ciphertext = AESGCM(key).encrypt(nonce, envelope, LOCAL_MAGIC)
            _atomic_write(self.snapshot, LOCAL_MAGIC + nonce + ciphertext)

    def restore(self) -> bytes | None:
        with _LOCK:
            if not self.snapshot.exists():
                return None
            with self.snapshot.open("rb") as stream:
                blob = stream.read(MAX_ENVELOPE_BYTES + 35)
            if not (34 <= len(blob) <= MAX_ENVELOPE_BYTES + 34 and blob.startswith(LOCAL_MAGIC)):
                raise ValueError("Invalid local vault")
            return AESGCM(self._key()).decrypt(blob[6:18], blob[18:], LOCAL_MAGIC)

    def wipe(self) -> None:
        with _LOCK:
            # Remove the key first so an interrupted wipe cannot recover the snapshot.
            self.key_path.unlink(missing_ok=True)
            self.snapshot.unlink(missing_ok=True)
