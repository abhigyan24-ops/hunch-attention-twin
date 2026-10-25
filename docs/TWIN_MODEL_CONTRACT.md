# Dhyan Twin Core Model Contract (v1.0.0)

This document formalizes the mathematical and statistical behavior of the Dhyan Attention Digital Twin core engine (`com.dhyan.app.twin`), resolving the specification requirements in spec sections 8, 9.1, 18.1, and 25.2–25.14.

---

## 1. Units and Outcome Window

- **Durations:** All modeled time loss, dwell times, and resumption delays are in **focus-minutes**.
- **Hazard Rate:** Baseline self-initiated hazard $\lambda$ is in **pickups per hour**. Rate per minute is $\lambda / 60.0$.
- **Outcome Window:** $[0, W)$ where default $W = 30.0\text{ min}$ (configurable via `TwinConfig.windowMin`).
- **Decision Instant ($t = 0$):** The moment the notification would be posted.
- **Single Owner Approximation (v1):** At most one distraction owner per arm per outcome window. Multiple distraction events occurring within a single 30-minute window are not modeled in v1.
- **Window Boundary Truncation:** By default (`truncateCostsAtWindow = false`), the full dwell and resumption duration ($D + R$) is charged if event onset occurs within the window. If `truncateCostsAtWindow = true`, cost is capped at $\min(D + R, \max(0, W - t_{\text{onset}}))$.

---

## 2. Competing Risks and Single-Owner Attribution

In the `SHOW` arm, the notification-triggered distraction competes with the user's natural baseline self-initiated distraction:

1. **Baseline Event:** Occurs at $T_{\text{self}} \sim \text{Exp}(\lambda_i / 60.0)\text{ min}$. Exists if $T_{\text{self}} < W$.
2. **Notification Event:** Occurs if $u_{\text{pick}} < p_i$ at pickup latency $T_{\text{notif}} \sim \text{LogNormal}(m_{\text{pl}}, s_{\text{pl}})\text{ min}$. Exists if picked and $T_{\text{notif}} < W$.
3. **Competing Risk Resolution (Earliest Event Wins):**
   - If both exist and $T_{\text{notif}} \le T_{\text{self}}$: Notification event owns the window. $L_{\text{show}} = c_{\text{glance}} + \text{cost}_{\text{notif}}$.
   - If both exist and $T_{\text{self}} < T_{\text{notif}}$: User was already distracted before opening the notification (in-session arrival). Baseline event owns the window. $L_{\text{show}} = c_{\text{glance}} + \text{cost}_{\text{self}}$.
   - If only notification exists: $L_{\text{show}} = c_{\text{glance}} + \text{cost}_{\text{notif}}$.
   - If only baseline exists: $L_{\text{show}} = c_{\text{glance}} + \text{cost}_{\text{self}}$.
   - If neither exists: $L_{\text{show}} = c_{\text{glance}}$.
4. **SUPPRESS Arm:** Suppression removes the notification opportunity. $L_{\text{suppress}} = \text{cost}_{\text{self}}$ if $T_{\text{self}} < W$, else $0.0$.
5. **No Double-Counting:** In no run does the SHOW arm charge both notification cost and baseline cost.

---

## 3. Common Random Numbers (CRN) Coupling

To isolate the causal effect $\Delta L = L_{\text{show}} - L_{\text{suppress}}$ from exogenous background noise, simulations use three independent seeded random streams derived via `RngStreams.stream(seed, runIndex, tag)`:

| Stream Tag | Scope | Draws & Semantics |
|---|---|---|
| `EPISTEMIC` | Shared by both arms | Epistemic parameter draws: $p_i \sim \text{Beta}$, $\lambda_i \sim \text{Gamma}$, $\theta_{\text{self}}, \theta_{\text{notif}} \sim \text{Dirichlet}$, and mean shift normals $\epsilon_{D\text{self}}, \epsilon_{D\text{notif}}, \epsilon_R$. |
| `SELF` | Shared by both arms | Baseline hazard draws: $u_{\text{time}}, u_{\text{dest}}, z_D, z_R$. Unconditionally drawn every run to maintain alignment across branches. |
| `NOTIF` | `SHOW` arm only | Notification draws: $u_{\text{pick}}, z_{\text{pl}}, u_{\text{dest}}, z_D, z_R$. Unconditionally drawn every run. |

Because `SELF` draws are coupled between `SHOW` and `SUPPRESS`, when a baseline distraction occurs in both arms, its realization is identical and cancels out of $\Delta L$, substantially reducing Monte Carlo variance.

---

## 4. Mathematical Identities

The implementation guarantees the following identities:

1. **Zero Pickup Probability:** When $p_{\text{pickup}} = 0$, $L_{\text{show}} = c_{\text{glance}} + L_{\text{suppress}}$ in every run. Thus $\Delta L_i = c_{\text{glance}}$ identically. When $c_{\text{glance}} = 0$, $\Delta L_i = 0.0$ identically.
2. **Zero Baseline Hazard ($\lambda = 0$):** $L_{\text{suppress}} = 0.0$. Expected $\Delta L = c_{\text{glance}} + p \cdot (E[D] + E[R]) \cdot P(T_{\text{notif}} < W)$.
3. **High Baseline Hazard ($\lambda \to \infty$):** Baseline event almost always precedes notification. Expected $\Delta L \to c_{\text{glance}}$.

---

## 5. Statistical Estimators and Hierarchy

### Beta Probability Estimator
- Counts $(s, f)$ decayed by $\gamma = 0.995$ per episode before adding new observations.
- Shrinkage with parent mean $\mu_p$ and strength $\kappa = 5$:
  $$\hat{p} = \frac{\alpha_0 + s + \kappa \mu_p}{\alpha_0 + \beta_0 + s + f + \kappa}$$
- Posterior sampling: $\text{Beta}(\alpha_0 + s + \kappa \mu_p, \beta_0 + f + \kappa(1 - \mu_p))$.

### Lognormal Duration Estimator
- Online Welford algorithm on $\ln(d)$ (count, mean, $M2$).
- Prior pseudo-observations $n_0 = 3$ with $(m_0, s_0)$ from population prior.
- $\sigma$ floored at $\sigma_{\text{min}} = 0.25$ during sampling.

### Gamma Baseline Hazard Estimator
- Events and exposure hours: $\text{Gamma}(\alpha_0 + \text{events}, \beta_0 + \text{exposureHours})$ (per hour).

### Hierarchy Backoff
- `FULL` $\to$ `PARENT` $\to$ `GRANDPARENT` $\to$ `PRIOR`.
- Effective sample size: $n_{\text{eff}} = n_{\text{full}} + 0.5 \cdot \max(0, n_{\text{parent}} - n_{\text{full}})$.

---

## 6. Output Quantities and Labeling

- **Percentiles:** $p_{10}, p_{50}, p_{90}$ calculated using linear interpolation at index $q \cdot (N - 1)$.
- **Strict Exceedance:** $P(L > 1.0\text{ min})$ (strict $>$, not $\ge$).
- **Simulation Interval:** $\Delta L$ 10th and 90th percentiles are labeled **"80% simulation interval"** (never credible interval).
- **Evidence Strength:** $n_{\text{eff}} / (n_{\text{eff}} + 10)$ is labeled **"heuristic evidence-strength score, not a probability of correctness"**.
- **Monte Carlo Standard Error:** Reported separately as $\text{SE} = \sqrt{\text{Var}(\Delta L) / N}$.

---

## 7. Fail-Open Safety Rules (Spec 25.5)

`TwinEngine.simulate` returns `SimOutcome.NotReady(reason)` when:
1. `MODEL_NOT_LOADED`: In-memory store has not completed `load()`.
2. `INVALID_SNAPSHOT`: Snapshot has invalid values ($ts \le 0$, confidence $< 0$ or $> 1$, negative duration).
3. `STALE_CONTEXT`: Current time minus snapshot timestamp exceeds `staleSnapshotMs` (120 s).

**Rule:** Callers must treat `NotReady` as `ALLOW`. Suppressing notifications during unready or stale states is strictly forbidden.
