# Hunch Attention Twin — Unified On-Device Machine Learning & Contextual Bandit Specification

**Repository:** `https://github.com/abhigyan24-ops/hunch-attention-twin`  
**Scope:** Full-Stack Unified Execution (`phone/`, `engine/`, `laptop/`, `vscode-ext/`)  
**Status:** Production Architecture

---

## 1. Architectural Overview & Invisible Math Policy
Hunch replaces hardcoded heuristic timers with a real, self-learning **Online Logistic Hazard Survival Model** paired with a **4-Arm Diagonal LinUCB Contextual Bandit** running natively on both Android companion devices (`phone/**/ml/`) and the local desktop engine (`engine/twin/ml_model.py`).

### 1.1 Non-Negotiable Engineering Principles
1. **Zero External Binary / Native Dependencies:**
   - Pure Kotlin (`DoubleArray`, `kotlin.math`) on Android.
   - Pure Python (`math`, `sqlite3`) in the FastAPI sidecar engine.
   - Zero C++ runtime binaries (no TensorFlow Lite, ONNX, or PyTorch), ensuring deterministic builds in under 60 seconds across all platforms and `< 0.1 ms` inference latency.
2. **Invisible Math UX:**
   - Raw hazard rates ($h_t$), bandit propensity scores, Brier loss floats, and UCB exploration bonuses are strictly internal.
   - Client user interfaces (VS Code status bar, Desktop Dashboard, Android companion) expose exclusively plain-English predictions (e.g., *"Likely distraction in ~25 mins — High app switching"*), 1-tap adaptive focus durations, and contextual nudge recommendations.
3. **Privacy Rule P5 (Zero Leakage):**
   - Feature vectors consist solely of anonymized counts, cyclical time ratios, and normalized durations. Raw notification texts, file names, URLs, and keystroke contents never enter feature vectors or model weights.

---

## 2. 12-Dimensional Normalized Feature Vector ($\mathbf{x}_t \in [0.0, 1.0]^{12}$)
Both `AttentionFeatureExtractor.kt` on Android and `extract_features()` in the Python engine transform raw sensor telemetry into an identical 12-element normalized vector:

| Index | Feature Name | Computation / Clamping Formula | Range | Rationale |
| :---: | :--- | :--- | :---: | :--- |
| `x[0]` | **Bias Term** | Constant `1.0` | `[1.0, 1.0]` | Baseline hazard intercept. |
| `x[1]` | **Cyclical Hour Sine** | `(sin(2 * pi * continuousHour / 24.0) + 1.0) / 2.0` | `[0.0, 1.0]` | Continuous circadian time of day (smooth transition across midnight). |
| `x[2]` | **Cyclical Hour Cosine** | `(cos(2 * pi * continuousHour / 24.0) + 1.0) / 2.0` | `[0.0, 1.0]` | Orthogonal circadian phase component. |
| `x[3]` | **Session Elapsed Duration** | `min(1.0, max(0.0, sessionElapsedMins / 90.0))` | `[0.0, 1.0]` | Attention span exhaustion scaling up to 90 minutes. |
| `x[4]` | **Circadian Sleep Debt** | `min(1.0, max(0.0, (480.0 - lastNightSleepMins) / 240.0))` | `[0.0, 1.0]` | Deficit below 8 hours (480m) scaled over 4 hours (240m). |
| `x[5]` | **Phone Unlock Velocity** | `min(1.0, max(0.0, unlocksLast15m / 8.0))` | `[0.0, 1.0]` | Rolling 15-minute phone pickup frequency. |
| `x[6]` | **App / Window Switches** | `min(1.0, max(0.0, switchesLast15m / 20.0))` | `[0.0, 1.0]` | Desktop/mobile window thrashing frequency. |
| `x[7]` | **Notification Pressure** | `min(1.0, max(0.0, notificationsLast15m / 15.0))` | `[0.0, 1.0]` | Incoming alert distraction burden over 15 minutes. |
| `x[8]` | **Cognitive Thrash** | `1.0 if isThrashing else 0.0` | `[0.0, 1.0]` | Active build failure streak or undo/delete churn. |
| `x[9]` | **Cumulative Work Fatigue** | `min(1.0, max(0.0, minsSinceLastBreak / 180.0))` | `[0.0, 1.0]` | Time since last physical break scaled to 3 hours (180m). |
| `x[10]`| **Dopamine Feed Exposure** | `min(1.0, max(0.0, socialScrollSecsLast30m / 600.0))` | `[0.0, 1.0]` | Time spent scrolling feeds in past 30m scaled to 10m. |
| `x[11]`| **Fatigue x Sleep Interaction** | `x[3] * x[4]` | `[0.0, 1.0]` | Compounding interaction between session length and sleep loss. |

---

## 3. Online Logistic Hazard Model & Discrete-Time Survival Rollout

### 3.1 5-Minute Instantaneous Hazard Probability
The probability $h_t$ that deep focus will be broken in the next 5-minute interval:
$$z_t = \mathbf{w}^\top \mathbf{x}_t = \sum_{i=0}^{11} w_i x_{t,i}$$
$$h_t = \sigma(z_t) = \begin{cases} \frac{1}{1 + e^{-z_t}}, & z_t \ge 0 \\ \frac{e^{z_t}}{1 + e^{z_t}}, & z_t < 0 \end{cases}$$
To prevent numerical instability, $h_t$ is clamped to $[10^{-6}, 1 - 10^{-6}]$.

### 3.2 18-Step Discrete-Time Survival Rollout (0 to 90 Minutes)
Starting from initial survival probability $S(0) = 1.0$, the engine rolls out 18 forward 5-minute intervals ($k = 1, 2, \dots, 18$):
1. For step $k$, construct hypothetical future context $\mathbf{x}_{t+k}$:
   - `sessionElapsedMins_k = sessionElapsedMins + 5.0 * k`
   - `minsSinceLastBreak_k = minsSinceLastBreak + 5.0 * k`
   - `continuousHour_k = (continuousHour + (5.0 * k) / 60.0) % 24.0`
2. Evaluate step hazard $h_{t+k} = \sigma(\mathbf{w}^\top \mathbf{x}_{t+k})$.
3. Update cumulative survival:
   $$S(k) = S(k-1) \cdot (1 - h_{t+k})$$
4. The predicted time until distraction $T^*$ is the first step where survival drops to median survival ($\le 0.50$):
   $$k^* = \min \{ k \in [1, 18] : S(k) \le 0.50 \}, \quad \text{predictedMinutes} = 5 \cdot k^*$$
   If $S(18) > 0.50$, the forecast defaults to $90\text{ mins}$.

### 3.3 Online AdaGrad Backpropagation (`trainOnlineStep` / `partial_fit`)
Given the actual 5-minute outcome $y_t \in \{0.0, 1.0\}$ ($1.0$ if focus broke due to an external distraction, $0.0$ if deep work was preserved):
1. Compute prediction error:
   $$e_t = h_t - y_t$$
2. Update rolling Brier Loss Exponential Weighted Moving Average (EWMA):
   $$L_t = 0.95 L_{t-1} + 0.05 e_t^2$$
3. For each feature $i \in \{0, 1, \dots, 11\}$:
   - Compute gradient with L2 weight decay regularization ($\lambda = 0.001$):
     $$g_i = e_t x_{t,i} + \lambda w_i$$
   - Accumulate squared gradient:
     $$G_i \leftarrow G_i + g_i^2$$
   - Apply AdaGrad parameter update ($\eta = 0.15$, $\epsilon = 10^{-8}$):
     $$w_i \leftarrow w_i - \frac{\eta}{\sqrt{G_i + \epsilon}} g_i$$
4. Increment sample counter $N \leftarrow N + 1$. Calibration status is marked active once $N \ge 6$.

---

## 4. 4-Arm Diagonal LinUCB Contextual Bandit
To deliver proactive flow interventions without annoying the user, Hunch uses a 4-Arm Contextual Upper Confidence Bound (LinUCB) bandit with diagonal covariance approximation:

### 4.1 Action Space
- **Arm 0 (`STAY_SILENT`):** No nudge or prompt; user is in stable flow.
- **Arm 1 (`AUTO_QUIET_SHIELD`):** Silently enable notification holding and compile shields.
- **Arm 2 (`MICRO_BREAK_PROMPT`):** Calmly suggest a 3-minute physical break / walk-away.
- **Arm 3 (`PARK_CONTEXT_PROMPT`):** Suggest 1-click intention parking before cognitive thrash escalates.

### 4.2 Decision Rule
For each arm $a \in \{0, 1, 2, 3\}$, parameterized by preference vector $\boldsymbol{\theta}_a \in \mathbb{R}^{12}$ and diagonal feature counts $\mathbf{c}_a \in \mathbb{R}^{12}$ (initialized to $1.0$):
$$\text{Score}(a \mid \mathbf{x}_t) = \boldsymbol{\theta}_a^\top \mathbf{x}_t + \alpha \sqrt{\sum_{i=0}^{11} \frac{x_{t,i}^2}{c_{a,i}}}$$
The parameter $\alpha = 0.25$ balances exploitation of known preferences with exploration of untried nudges.

### 4.3 Reward Function & Online Update
- **Reward Assignment ($r \in [-1.0, +1.0]$):**
  - $+1.0$: Focus persisted for $\ge 15\text{ mins}$ post-intervention or user tapped "Park" / "Accept".
  - $-0.5$: User dismissed the prompt or switched apps within 3 minutes of intervention.
- **Online Parameter Update:**
  $$c_{a^*, i} \leftarrow c_{a^*, i} + x_{t,i}^2$$
  $$\hat{r} = \boldsymbol{\theta}_{a^*}^\top \mathbf{x}_t, \quad e_r = r - \hat{r}$$
  $$\theta_{a^*, i} \leftarrow \theta_{a^*, i} + \frac{0.10 \cdot x_{t,i}}{\sqrt{c_{a^*, i}}} e_r$$

---

## 5. Federated Weight Synchronization & Persistence

### 5.1 Sample-Weighted Federated Averaging (`POST /v1/ml/sync-weights`)
When the phone connects to the local Python engine sidecar, models merge via sample-weighted averaging:
$$\mathbf{w}_{\text{merged}} = \frac{N_p \mathbf{w}_p + N_e \mathbf{w}_e}{\max(1, N_p + N_e)}$$
$$\mathbf{G}_{\text{merged}} = \frac{N_p \mathbf{G}_p + N_e \mathbf{G}_e}{\max(1, N_p + N_e)}$$
$$N_{\text{merged}} = N_p + N_e, \quad L_{\text{merged}} = \frac{N_p L_p + N_e L_e}{\max(1, N_p + N_e)}$$

### 5.2 Encrypted Persistence
- **Android (`phone/`):** Serializes JSON payload into private `EncryptedVaultManager` (AES-256-GCM hardware-backed keystore) surviving app uninstalls and reboots.
- **Desktop (`engine/`):** Persists model state into SQLite table `ml_model_state` (`~/.hunch/hunch.db`).
