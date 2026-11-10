# Third-Party Notices

Hunch incorporates third-party open-source software under their respective licenses. This document lists the components, their licenses, and copyright notices.

---

## 1. Laptop Desktop App (`laptop/`)

| Component | Version / Spec | License | Source / Link |
|---|---|---|---|
| `@google/genai` | `^0.1.1` | Apache-2.0 | https://github.com/google-gemini/dep-genai-js |
| `ajv` | `^8.17.1` | MIT | https://github.com/ajv-validator/ajv |
| `ajv-formats` | `^3.0.1` | MIT | https://github.com/ajv-validator/ajv-formats |
| `electron-log` | `^5.2.0` | MIT | https://github.com/megahertz/electron-log |
| `electron-updater` | `^6.3.9` | MIT | https://github.com/electron-userland/electron-builder |
| `react` | `^18.3.1` | MIT | https://github.com/facebook/react |
| `react-dom` | `^18.3.1` | MIT | https://github.com/facebook/react |
| `ws` | `^8.18.0` | MIT | https://github.com/websockets/ws |
| `zod` | `^3.23.8` | MIT | https://github.com/colinhacks/zod |
| `electron` (Build dependency) | `^31.4.0` | MIT | https://github.com/electron/electron |
| `electron-builder` (Build dependency) | `^24.13.3` | MIT | https://github.com/electron-userland/electron-builder |
| `electron-vite` | `^2.3.0` | MIT | https://github.com/alex8088/electron-vite |

*Note on Electron & Chromium:* The Windows installer includes `LICENSE.electron.txt` and `LICENSES.chromium.html` in accordance with Electron redistribution guidelines.

---

## 2. VS Code Extension (`vscode-ext/`)

| Component | Version / Spec | License | Source / Link |
|---|---|---|---|
| `ws` | `^8.18.0` | MIT | https://github.com/websockets/ws |
| `esbuild` (Build dependency) | `^0.24.0` | MIT | https://github.com/evanw/esbuild |
| `@vscode/vsce` (Build dependency) | `^3.1.1` | MIT | https://github.com/microsoft/vscode-vsce |
| `@vscode/test-electron` (Build dependency) | `^2.4.1` | MIT | https://github.com/microsoft/vscode-test-electron |

---

## 3. Encrypted Relay (`relay/`)

| Component | Version / Spec | License | Source / Link |
|---|---|---|---|
| `ws` | `^8.18.0` | MIT | https://github.com/websockets/ws |
| `ajv` | `^8.17.1` | MIT | https://github.com/ajv-validator/ajv |
| `ajv-formats` | `^3.0.1` | MIT | https://github.com/ajv-validator/ajv-formats |

---

## 4. Causal & Attention Engine (`engine/`)

| Component | Version / Spec | License | Source / Link |
|---|---|---|---|
| `fastapi` | `>=0.110.0` | MIT | https://github.com/tiangolo/fastapi |
| `uvicorn` | `>=0.28.0` | BSD-3-Clause | https://github.com/encode/uvicorn |
| `pydantic` | `>=2.6.0` | MIT | https://github.com/pydantic/pydantic |
| `numpy` | `>=1.24.0` | BSD-3-Clause | https://github.com/numpy/numpy |
| `pandas` | `>=2.1.0` | BSD-3-Clause | https://github.com/pandas-dev/pandas |
| `scikit-learn` | `>=1.4.0` | BSD-3-Clause | https://github.com/scikit-learn/scikit-learn |
| `lightgbm` | `>=4.0.0` | MIT | https://github.com/microsoft/LightGBM |
| `lifelines` | `>=0.28.0` | MIT | https://github.com/CamDavidsonPilon/lifelines |
| `dowhy` | `>=0.11.0` | MIT | https://github.com/py-why/dowhy |
| `econml` | `>=0.15.0` | BSD-3-Clause | https://github.com/py-why/EconML |
| `statsmodels` | `>=0.14.0` | BSD-3-Clause | https://github.com/statsmodels/statsmodels |
| `pytest` | `>=8.0.0` | MIT | https://github.com/pytest-dev/pytest |
| `httpx` | `>=0.27.0` | BSD-3-Clause | https://github.com/encode/httpx |

*Note on PyInstaller:* Bundling the standalone engine executable uses PyInstaller, which is GPL licensed with a special exception permitting distribution of the bundled application under the developer's choice of license.

---

## 5. Android Smartphone App (`phone/`)

| Component | License | Source / Link |
|---|---|---|
| AndroidX Jetpack Compose (`ui`, `material3`, `runtime`) | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support |
| AndroidX Room (`runtime`, `ktx`) | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support |
| AndroidX WorkManager (`runtime`) | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support |
| KotlinX Coroutines (`core`, `android`) | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| OkHttp | Apache-2.0 | https://github.com/square/okhttp |
| Koin Android (`koin-android`, `koin-androidx-compose`) | Apache-2.0 | https://github.com/InsertKoinIO/koin |
| JUnit 4 | EPL-2.0 | https://github.com/junit-team/junit4 |

---

## 6. Graphic Assets & Characters

| Asset | Description | Source / Provenance | License |
|---|---|---|---|
| Pip Mascot Sprite | Digital attention companion character | Original creation for Hunch | Apache-2.0 |
| Application Icons & SVGs | App icon, status bar, tray icons | Original project vector assets | Apache-2.0 |
