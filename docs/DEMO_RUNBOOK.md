# Hunch: 4–5 Minute Live Demonstration Runbook & Rehearsal Script

**Spec Reference:** §19 (Demo Script) & §20 (Pitch Q&A)  
**Target Setup:** Oppo Reno 14 (Android App) + Laptop Workstation (Desktop App & VS Code Extension) + Local/Cloud Encrypted Relay

---

## 1. Pre-Flight Setup Checklist (5 Minutes Before Demo)

### 1.1 Phone (Oppo Reno 14)
1. Connect via USB or ensure phone and laptop are on the same local WiFi / hotspot.
2. Confirm USB port-forwarding (if USB attached):
   ```powershell
   adb reverse tcp:8080 tcp:8080
   ```
3. Launch **Dhyan** (`com.dhyan.app`).
4. Ensure **Foreground Service** notification is running (*"Hunch Active · Monitoring attention"*).
5. Verify in **Settings** (bottom sheet) that:
   - Policy Mode is set to **POLICY MODE: LIVE** (or **DRY_RUN** if testing without notification interception).
   - Kill Switch is **OFF**.

### 1.2 Laptop Workstation
1. Start the Encrypted Relay daemon:
   ```powershell
   node relay/server.js
   ```
2. Launch the Desktop App:
   ```powershell
   cd laptop
   npm run dev
   ```
3. Open **VS Code** with the Hunch Extension enabled:
   - Check the bottom status bar: `$(pulse) Hunch: 24:15 • Focused (92%)`.
   - Check Activity Bar: Click the Hunch icon to display the **Hunch Companion** panel with the Cloud Spirit pet.

### 1.3 Pairing Check
- In Desktop App Settings or Reno 14 Settings, ensure paired status displays: `● Connected · Confirmed SAS [607 383]`.

---

## 2. Minute-by-Minute Live Presentation Script

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       4–5 MINUTE DEMO FLOW AT A GLANCE                      │
│                                                                             │
│  [0:00 - 0:20] Hook: The 24-minute cost of a single "like"                  │
│  [0:20 - 0:40] Cross-Device Presence: Meet the Frosted Cloud Spirit        │
│  [0:40 - 1:40] The Hero Scenario: Rahul, Coding, 4 hrs in (Digest Hold)     │
│  [1:40 - 2:10] The Safety Guarantee: Mom's urgent call passes immediately   │
│  [2:10 - 2:40] Counterfactuals & Personas: Kabir's self-distraction         │
│  [2:40 - 3:25] Twin Lab Posterior Updating: Real-time Bayesian learning    │
│  [3:25 - 3:55] Intentionality vs Compulsion: Lectures vs Shorts             │
│  [3:55 - 4:25] Real Data & 6 Insights Charts: Mathematical transparency     │
│  [4:25 - 4:45] Close: Zero-Cloud Privacy & 100% Safety Guarantees          │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### Phase 1: The Hook (0:00 – 0:20)
* **Presenter Action:** Hold up phone in one hand; point to the laptop screen with VS Code open.
* **Verbal Script:**
  > *"According to research from Gloria Mark at UC Irvine, recovering from a single mid-task distraction takes an average of over 20 minutes of cognitive switching lag.
  >
  > Today’s operating systems offer crude Do Not Disturb toggles that either block everything or let everything through.
  >
  > We built **Hunch** — an on-device digital twin that simulates both futures: what happens if we show you this notification right now, versus what happens if we hold it for a scheduled digest. And it does this in under half a millisecond without ever reading your message text."*

---

### Phase 2: Cross-Device Presence — Meet the Cloud Spirit (0:20 – 0:40)
* **Presenter Action:** Type a few lines in VS Code. Point to the VS Code sidebar companion panel and the Desktop Bento widget.
* **Verbal Script:**
  > *"Meet our companion pet, the Hunch Cloud Spirit. Notice how as I type in VS Code, its eyes track my cursor saccades and it stays in deep focus.
  >
  > Across both my laptop and the Reno 14 phone in my hand, the attention state is synchronized in real-time over an end-to-end encrypted relay using P-256 ECDH keys."*

---

### Phase 3: The Hero Scenario — Predictive Digest Hold (0:40 – 1:40)
* **Presenter Action:**
  1. Open the phone to the **Simulate** tab.
  2. Select persona **"Rahul"** (Software Engineer, 4 hours worked today, circadian afternoon).
  3. Fire the synthetic notification: *"Someone liked your post"* (Category: `social_notif`).
  4. Point out the two simulation timelines on screen.
* **Verbal Script:**
  > *"Here is Rahul, a software engineer 4 hours into a coding sprint. A social media ping arrives.
  >
  > Hunch’s twin instantly simulates the counterfactual:
  > - If shown now: 62% pickup probability leads to an expected focus loss of 24 minutes.
  > - If held: only 2 minutes of baseline hazard.
  >
  > The verdict is **DIGEST HELD** with a clear one-line explanation: 'High distraction risk during deep coding.'
  >
  > On the laptop, the companion pet perks up and displays: '1 held'. The user was never interrupted."*

---

### Phase 4: Fail-Open Safety & Never-Hold Guarantee (1:40 – 2:10)
* **Presenter Action:**
  1. On the phone Simulate tab, fire *"Mom: Call me, urgent"* or a Bank OTP.
  2. The notification passes through instantly as **ALLOW**.
* **Verbal Script:**
  > *"Hunch is not a blocker. When an urgent message arrives — an emergency keyword, a family member, or a bank authentication OTP — the twin does not hold it.
  >
  > Our safety rules are absolute: across 9,072 stress-test decisions across all modes, zero urgent notifications were ever held. The system fails open unconditionally."*

---

### Phase 5: Counterfactuals & Personas (2:10 – 2:40)
* **Presenter Action:** Switch persona dropdown to **"Kabir"** (Student, high spontaneous self-interruption rate).
* **Verbal Script:**
  > *"Notice how the twin adapts to different human behavioral dynamics.
  >
  > When we switch to Kabir, who has a high spontaneous self-distraction rate, the twin predicts only 1.2 minutes saved by suppressing the notification, because Kabir is likely to self-interrupt anyway. 
  >
  > Hunch doesn't use generic heuristics — it models individual attention probability."*

---

### Phase 6: Twin Lab — Real-Time Posterior Learning (2:40 – 3:25)
* **Presenter Action:**
  1. Switch to **Live & Digest** tab.
  2. Tap **"That was important"** on a held card.
  3. Switch to the **Insights** tab and expand the **Twin Lab (Posterior Convergence)** drawer.
* **Verbal Script:**
  > *"Hunch personalizes without heavy neural networks or cloud training.
  >
  > When the user provides feedback by tapping 'That was important', Hunch performs conjugate Bayesian updating on the Beta distribution parameters.
  >
  > In the Twin Lab, you can see the posterior probability curve shift to the right in real-time, increasing effective observations $n_{\text{eff}}$. Tomorrow, similar notifications will be weighted accordingly."*

---

### Phase 7: Intentionality vs Compulsion (3:25 – 3:55)
* **Presenter Action:** Point to the **Intentional vs. Compulsive** ratio bar ($68\%$ vs $32\%$) and the Circadian Fatigue curve on the Insights tab.
* **Verbal Script:**
  > *"Hunch also differentiates between deep, intentional media engagement and compulsive short-form scrolling.
  >
  > Long lecture videos on YouTube maintain stable dwell with low app-switching, scoring high on intentionality. In contrast, short-form reels trigger high fragmentation and are surfaced for break boundaries."*

---

### Phase 8: Real Data & 6 Insights Charts (3:55 – 4:25)
* **Presenter Action:** Scroll through the 6 Insights charts on the phone:
  - Focus-Hour Heatmap
  - Interruption Triggers by App
  - Surface Dwell Distributions
  - Importance Beliefs
  - Circadian Attention Curve
  - Fragmentation Index
* **Verbal Script:**
  > *"On this screen, you see our full analytical suite: 6 interactive charts computed from local Room database logs.
  >
  > It highlights peak vulnerability hours, identifies top fragmenting apps, and quantifies estimated focus minutes saved."*

---

### Phase 9: Wrap-Up & Evaluator Takeaways (4:25 – 4:45)
* **Presenter Action:** Show the Settings sheet with the Global Kill Switch.
* **Verbal Script:**
  > *"To summarize:
  > 1. **Complete Privacy:** 23 forbidden fields verified by AST scanners. No text, titles, URLs, or keystrokes ever leave this device.
  > 2. **Sub-Millisecond Speed:** In-memory decision latency under 0.5 milliseconds.
  > 3. **Mathematical Guarantees:** 100% fail-open safety, zero urgent items delayed, and full cross-device synchronization.
  >
  > Thank you, and we welcome your questions."*

---

## 3. Evaluator Q&A Cheat Sheet (Prepared Answers)

1. **Q: Why not just use Android Do Not Disturb or Focus Mode?**
   * *Answer:* DND is a static binary hammer. It blocks everything or nothing. Hunch is a probabilistic twin that calculates the counterfactual cost of interruption in *this specific moment* based on your current task and circadian rhythm, holding only low-urgency distractions while guaranteeing emergency delivery.

2. **Q: How can you guarantee privacy if you monitor notifications?**
   * *Answer:* Hunch enforces a 23-forbidden-field AST scanner at build time. We never store, log, or transmit raw notification text, sender names, URLs, or keystrokes. Only categorical tokens (e.g. `social_notif`, `coding`) and mathematical count frequencies are processed.

3. **Q: What if the app crashes? Will I miss an emergency call or OTP?**
   * *Answer:* Hunch is built fail-open by design. If any component encounters an exception or delay, all holds fail open instantly and the notification is displayed normally. Alarms, phone calls, and OTPs completely bypass the twin engine through our hardcoded Never-Hold rules.

4. **Q: Why use Bayesian conjugate priors instead of a deep neural network or LLM?**
   * *Answer:* Conjugate Beta and Log-Normal distributions update in $O(1)$ time with zero matrix multiplications. They consume near-zero CPU and battery, provide exact mathematical confidence bounds (epistemic uncertainty), and achieve a decision latency of under 0.5 ms without requiring cloud GPUs.

5. **Q: What happens if the laptop or WiFi goes offline?**
   * *Answer:* The phone twin operates completely independently with full on-device intelligence. When reconnecting after an outage, our protocol performs monotonic replay backfill and deduplication with zero data loss, as verified in our 60-second drop test.

---

## 4. Emergency Backup & Fallback Playbook

| Scenario | Immediate Action during Presentation |
|---|---|
| **WiFi / Network Drops** | Continue without interruption: The phone and laptop twin engines run 100% locally on-device. State will buffer and sync automatically when reconnected. |
| **USB Disconnects from Phone** | The phone app continues running untethered on battery. You can either reconnect USB and run `adb reverse tcp:8080 tcp:8080` or rely on local WiFi. |
| **Relay Process Terminates** | Re-run `node relay/server.js` in your terminal. Both phone and laptop will automatically reconnect within 2 seconds. |
| **Physical Notification Not Arriving** | Switch to the **Simulate** tab and trigger the pre-configured synthetic events (Rahul coding / Social notif / Urgent call). The simulation runs on the exact same production `TwinEngine` code. |
| **Evaluator Questions Replay Legitimacy** | Open terminal and run `npm run validate:pitch`. Show the live terminal output proving 8/8 suites passed (parameter recovery error = 0.0084, 0 urgent delayed across 9,072 cases, median latency < 0.5 ms). |
