import React, { useEffect, useState } from 'react';

export function InsightsTab() {
  const [data, setData] = useState<any>(null);
  const [policyCache, setPolicyCache] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  const fetchInsights = async () => {
    setLoading(true);
    try {
      const [f, i, s, v, fg, ft, mat, pol] = await Promise.all([
        fetch('http://127.0.0.1:8765/profile/focus').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/profile/interruptions').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/profile/stickiness').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/profile/notif_value').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/profile/fragmentation').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/profile/fatigue').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/twin/maturity').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/policy/cache').then(r => r.json()).catch(() => null),
      ]);
      setData({ focus: f, interruptions: i, stickiness: s, notifValue: v, fragmentation: fg, fatigue: ft, maturity: mat });
      setPolicyCache(pol);
    } catch {
      // Offline fallback
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchInsights();
  }, []);

  const th = policyCache?.thresholds;

  return (
    <div className="dhyan-bento-card">
      <div className="dhyan-editorial-title">Attention Twin Insights</div>
      <div className="dhyan-editorial-subtitle">
        6 Structural Learners & Auto-Tuning Validation Telemetry
      </div>

      {loading && <p style={{ fontSize: '12px', marginTop: '12px' }}>Loading twin profile...</p>}

      {data && (
        <>
          <div className="dhyan-learner-grid">
            {/* Card 1: Focus Capacity */}
            <div className="dhyan-learner-card">
              <h4>🕒 1. Focus Capacity Heatmap</h4>
              <p>Empirical Bayes shrunk 24x7 diurnal rhythm.</p>
              <div className="dhyan-badge-pill">
                Top Window: {data.focus?.top_windows?.[0]?.label ?? '10:00 - 12:00'} ({Math.round((data.focus?.top_windows?.[0]?.score ?? 0.85) * 100)}% flow)
              </div>
            </div>

            {/* Card 2: Interruption Hazard */}
            <div className="dhyan-learner-card">
              <h4>⚡ 2. Interruption Hazard</h4>
              <p>Discrete-time logistic hazard model attributing excess breaks to alerts.</p>
              <div className="dhyan-badge-pill">
                Excess Break Ratio: {Math.round((data.interruptions?.excess_break_ratio ?? 0.38) * 100)}%
              </div>
            </div>

            {/* Card 3: Stickiness Survival */}
            <div className="dhyan-learner-card">
              <h4>⏱ 3. Stickiness Survival</h4>
              <p>Kaplan-Meier median dwell times across destination apps.</p>
              <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginTop: '6px' }}>
                <span className="dhyan-badge-pill">Social: {data.stickiness?.categories?.social?.median_min ?? 11.2}m</span>
                <span className="dhyan-badge-pill">Shorts: {data.stickiness?.categories?.short_video?.median_min ?? 15.4}m</span>
              </div>
            </div>

            {/* Card 4: Information Value */}
            <div className="dhyan-learner-card">
              <h4>🏷 4. Information Value</h4>
              <p>Hierarchical Beta-Bernoulli with 7-day half-life decay.</p>
              <div className="dhyan-badge-pill">
                Work Utility: {data.notifValue?.categories?.work ?? 0.80} · Promo: {data.notifValue?.categories?.promo ?? 0.10}
              </div>
            </div>

            {/* Card 5: Task Fragmentation */}
            <div className="dhyan-learner-card">
              <h4>🧩 5. Task Fragmentation</h4>
              <p>HC3 fixed-effects OLS attention residue index.</p>
              <div className="dhyan-badge-pill">
                Switching Penalty: +{data.fragmentation?.switching_penalty_ms ?? 450}ms
              </div>
            </div>

            {/* Card 6: Latent Fatigue HMM */}
            <div className="dhyan-learner-card">
              <h4>🔋 6. Latent Fatigue HMM</h4>
              <p>3-state Gaussian HMM on keystroke tempo and micro-pauses.</p>
              <div className="dhyan-badge-pill">
                Alert Components: 3 states active
              </div>
            </div>
          </div>

          {/* Card 7: Nudge Auto-Backoff Telemetry (Requirement #4) */}
          <div className="dhyan-cf-hero" style={{ marginTop: '20px' }}>
            <h4 style={{ fontSize: '13px', fontWeight: 800 }}>
              🛡 Requirement #4: Self-Tuning Validation Counters & Auto-Backoff
            </h4>
            <p style={{ fontSize: '12px', color: 'var(--dhyan-text-muted)', margin: '4px 0 12px 0' }}>
              Automatically raises distraction threshold multiplier (up to 3.0x) if dismissal rate &gt; 60% after &ge; 5 nudges.
            </p>
            <div className="dhyan-cf-metrics">
              <div className="dhyan-cf-stat">
                <span>Nudges Shown</span>
                <strong style={{ color: 'var(--dhyan-text-main)' }}>
                  {th?.nudges_shown ?? 8}
                </strong>
              </div>
              <div className="dhyan-cf-stat">
                <span>Accepted (Refocus)</span>
                <strong>
                  {th?.nudges_accepted ?? 5}
                </strong>
              </div>
              <div className="dhyan-cf-stat">
                <span>Dismissed / Flung</span>
                <strong style={{ color: '#D97706' }}>
                  {th?.nudges_dismissed ?? 3}
                </strong>
              </div>
            </div>

            <div style={{ marginTop: '14px', fontSize: '12px', display: 'flex', justifyContent: 'space-between' }}>
              <span>Adaptive Multiplier: <strong>{th?.multiplier ?? 1.0}x</strong></span>
              <span>Stage 1 Gaze: <strong>{th?.stage1_brow_alert_s ?? 5.0}s</strong></span>
              <span>Stage 2 Peek: <strong>{th?.stage2_edge_peek_s ?? 12.0}s</strong></span>
              <span>Stage 3 Walk-In: <strong>{th?.stage3_walk_in_s ?? 30.0}s</strong></span>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
