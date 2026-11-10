# Contributing to Hunch

Thank you for contributing to Hunch! Because Hunch models attention and operates alongside personal user activity, our codebase adheres to non-negotiable architectural boundaries.

---

## The Privacy Contract (Non-Negotiable)

1. **Zero Telemetry:** No analytics, telemetry pings, crash reporters, or background phone-home mechanisms.
2. **Zero Content Collection:** Never capture, log, parse, or transmit keystrokes, message bodies, notification text, document content, file names, file paths, git branch names, commit messages, terminal command strings, or screen pixels. Only counts, durations, categories, and salted cryptographic hashes are permitted.
3. **Strict Network Isolation:** Data flows only between `localhost`, the user's cryptographically paired personal devices, and an optional end-to-end encrypted relay or user-configured LLM provider.
4. **Enforced Gate Sanitization:** All outbound events must be filtered through `PrivacyGate.sanitize`. No secondary or unvalidated bypass paths are permitted.
5. **Contract Enforcement:** Modifications to `PrivacyGate`, schemas in `contracts/`, or documented privacy guarantees require maintainer review and must pass automated CI drift tests.

---

## Project Structure

| Directory | Component | Language / Framework |
|---|---|---|
| `phone/` | Android Smartphone App | Kotlin, Jetpack Compose, Room DB, WorkManager |
| `laptop/` | Desktop Workstation App | Electron, TypeScript, React |
| `engine/` | Causal Inference & Learning Engine | Python, FastAPI, NumPy, SciPy, Scikit-learn |
| `vscode-ext/` | VS Code Attention Companion | TypeScript, VS Code API |
| `relay/` | Encrypted In-Memory Pairing Relay | Node.js, TypeScript, WebSocket |
| `contracts/` | Protocol Schemas & Test Vectors | JSON Schema, Markdown |
| `tools/` | Conformance, Purity & Drift Checkers | Node.js, Python |

---

## Local Development & Testing

```bash
# Root contract & tool checks
npm test
npm run check:contracts
node tools/rename-guard.mjs

# Laptop Desktop App
cd laptop
npm ci
npm run typecheck
npm test

# VS Code Extension
cd ../vscode-ext
npm ci
npm run lint
npm run test:unit

# Causal Engine (Python)
cd ../engine
python -m venv .venv
# On Windows: .venv\Scripts\activate | On POSIX: source .venv/bin/activate
pip install -r requirements.lock.txt
python -m pytest -q

# Android App
cd ../phone
./gradlew testDebugUnitTest
```

---

## Contribution Workflow

1. **Open an Issue First:** For non-trivial additions or behavioral changes, discuss the proposed design before submitting a PR.
2. **Branch from `main`:** Keep pull requests focused and atomic.
3. **Add Tests:** Maintain unit, purity, and contract tests for all modified components.
4. **Sign Off Your Commits (DCO):** All commits must include a `Signed-off-by:` trailer (`git commit -s`) certifying adherence to the [Developer Certificate of Origin](https://developercertificate.org/).
5. **AI-Assisted Contributions:** Contributors are welcome to use AI tools, but are solely responsible for verifying the accuracy, licensing, and security of all generated code.

---

## Reporting Issues & Conduct
- Report security issues following [SECURITY.md](SECURITY.md).
- Follow the [Code of Conduct](CODE_OF_CONDUCT.md).
