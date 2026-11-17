"""Generate an appeal packet only for a verified Hunch release APK."""
import argparse
import json
from pathlib import Path

from apk_hash_resolver import find_or_build_apk, verify_release

STATEMENT = """Please review Hunch, an open-source focus companion, for a possible incorrect classification.
G1: Review manifest permissions against the restricted permission list.
G2: Accessibility configuration declares window-state events and no window-content access. Review runtime behavior in A11yCollector as well.
G3: System and security notifications should bypass filtering. The source is available for review.
G4: Optional sensitive permissions require a user disclosure and an explicit Android Settings grant.
G5: Hunch supports leaving Play Protect enabled.
G6: The repository includes compliance and update-check unit tests. The packet verifies the APK identity and signatures separately.
A VirusTotal link identifies the exact file by hash and does not indicate that an analysis exists."""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk")
    parser.add_argument("--out", type=Path, default=Path("docs"))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    try:
        metadata = verify_release(find_or_build_apk(root, args.apk))
    except (OSError, ValueError) as exc:
        parser.exit(1, f"Appeal packet not generated: {exc}\n")
    statement = "\n".join([
        f"Package Name: {metadata['packageName']}",
        f"Version: {metadata['versionName']} (versionCode {metadata['versionCode']})",
        f"APK SHA-256: {metadata['apkSha256']}",
        f"Signing Certificate SHA-256: {metadata['signingCertSha256']}",
        f"VirusTotal Analysis URL: {metadata['virusTotalUrl']}",
        "", STATEMENT,
    ])
    metadata.update(developerName="Abhigyan (Hunch Open Source Project)",
                    subject="Request for Play Protect review of Hunch v2.0.0",
                    technicalStatement=statement)
    output = root / args.out
    output.mkdir(parents=True, exist_ok=True)
    (output / "play_protect_appeal_payload.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    summary = "\n".join(f"- **{key}:** `{metadata[key]}`" for key in (
        "packageName", "versionName", "versionCode", "apkSizeBytes", "apkSha256", "signingCertSha256"))
    (output / "PLAY_PROTECT_APPEAL_SUBMISSION_READY.md").write_text(
        f"# {metadata['subject']}\n\n{summary}\n\n"
        f"[VirusTotal file lookup]({metadata['virusTotalUrl']}) Â· [Google appeal form]({metadata['appealFormUrl']})\n\n"
        f"{statement}\n", encoding="utf-8")
    print(f"APK SHA-256: {metadata['apkSha256']}")


if __name__ == "__main__":
    main()
