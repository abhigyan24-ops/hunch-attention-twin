import hashlib
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "apk_hash_resolver", Path(__file__).resolve().parents[2] / "tools/apk_hash_resolver.py"
)
resolver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(resolver)


def test_hash_and_size_come_from_exact_bytes(tmp_path):
    path = tmp_path / "fixture.apk"
    data = b"synthetic hash fixture only"
    path.write_bytes(data)
    metadata = resolver.compute_apk_metadata(path)
    digest = hashlib.sha256(data).hexdigest()
    assert metadata["apkSha256"] == digest
    assert metadata["apkSizeBytes"] == len(data)
    assert metadata["virusTotalUrl"].endswith(f"/{digest}/detection")
    assert "signingCertSha256" not in metadata
