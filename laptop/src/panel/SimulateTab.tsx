import React, { useEffect, useState } from 'react';

export function SimulateTab() {
  const [multiplier, setMultiplier] = useState(1.0);
  const [result, setResult] = useState<any>(null);
  const [apps, setApps] = useState<string[]>([]);
  const [selectedApp, setSelectedApp] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [replay, setReplay] = useState<any>(null);
  const [selectedNotif, setSelectedNotif] = useState<string | null>(null);

  const fetchApps = async () => {
    try {
      const res = await fetch('http://127.0.0.1:8765/simulate/apps');
      if (res.ok) {
        const json = await res.json();
        if (Array.isArray(json.apps) && json.apps.length > 0) {
          setApps(json.apps);
        }
      }
    } catch {
      // Offline fallback
    }
  };

  const runSimulation = async (mult: number) => {
    setLoading(true);
    try {
      const res = await fetch('http://127.0.0.1:8765/whatif', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          threshold_multiplier: mult,
          urgency_cutoff: 0.40,
          horizon_hours: 24,
        }),
      });
      if (res.ok) {
        const json = await res.json();
        setResult(json.result);
        if (Array.isArray(json.apps) && json.apps.length > 0) {
          setApps(json.apps);
        }
      }
    } catch {
      // Sidecar offline fallback
    } finally {
      setLoading(false);
    }
  };

  const loadReplay = async (notifId: string) => {
    setSelectedNotif(notifId);
    try {
      const res = await fetch(`http://127.0.0.1:8765/replay/${encodeURIComponent(notifId)}`);
      if (res.ok) {
        const json = await res.json();
        setReplay(json);
      }
    } catch {
      // Offline fallback
    }
  };

  useEffect(() => {
    void fetchApps();
    void runSimulation(multiplier);
  }, []);

  const filteredDetails = (result?.details || []).filter((d: any) => {
    if (!selectedApp) return true;
    return d.category === selectedApp || d.app_pkg === selectedApp || d.notif_id?.includes(selectedApp);
  });

  return (
    <div className="dhyan-bento-card">
      <div className="dhyan-editorial-title">What-If Causal Simulator</div>
      <div className="dhyan-editorial-subtitle">
        Simulate counterfactual attention policies with Common Random Numbers (CRN)
      </div>

      {/* App Selector and Multiplier Slider */}
      <div style={{ marginTop: '16px' }}>
        {apps.length > 0 && (
          <div style={{ marginBottom: '12px' }}>
            <label style={{ fontSize: '12px', fontWeight: 700, display: 'block', marginBottom: '4px' }}>
              Observed Application Filter:
            </label>
            <select
              value={selectedApp}
              onChange={(e) => setSelectedApp(e.target.value)}
              style={{
                width: '100%',
                padding: '6px 10px',
                borderRadius: '8px',
                border: '1px solid var(--dhyan-border, #ccc)',
                background: 'var(--dhyan-card, #fff)',
                color: 'var(--dhyan-text-main, #333)',
                fontSize: '12px',
              }}
            >
              <option value="">All Observed Apps ({apps.length} available)</option>
              {apps.map((pkg) => (
                <option key={pkg} value={pkg}>
                  {pkg}
                </option>
              ))}
            </select>
          </div>
        )}

        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', fontWeight: 700 }}>
          <span>Policy Gatekeeper Multiplier</span>
          <span style={{ color: 'var(--dhyan-mint-accent)' }}>{multiplier.toFixed(2)}x</span>
        </div>
        <div className="dhyan-slider-row">
          <span style={{ fontSize: '11px', color: 'var(--dhyan-text-muted)' }}>Aggressive (0.5x)</span>
          <input
            type="range"
            min="0.5"
            max="2.5"
            step="0.1"
            className="dhyan-slider"
            value={multiplier}
            onChange={(e) => {
              const v = parseFloat(e.target.value);
              setMultiplier(v);
              void runSimulation(v);
            }}
          />
          <span style={{ fontSize: '11px', color: 'var(--dhyan-text-muted)' }}>Permissive (2.5x)</span>
        </div>
        <button
          className="dhyan-small-btn"
          onClick={() => void runSimulation(multiplier)}
          disabled={loading}
        >
          {loading ? 'Simulating...' : 'Re-run Monte Carlo Simulation ⚡'}
        </button>
      </div>

      {/* Counterfactual Comparison Card */}
      {result && result.notifications_total > 0 ? (
        <div className="dhyan-cf-hero" style={{ marginTop: '20px' }}>
          <h4 style={{ fontSize: '14px', fontWeight: 800, margin: 0 }}>
            Coupled Counterfactual Results (Equal Horizon H=3600s)
          </h4>
          <div className="dhyan-cf-metrics">
            <div className="dhyan-cf-stat">
              <span>Attention Saved</span>
              <strong>+{result.focus_minutes_saved} min</strong>
            </div>
            <div className="dhyan-cf-stat">
              <span>Breaks Prevented</span>
              <strong>-{result.interruptions_prevented}</strong>
            </div>
            <div className="dhyan-cf-stat">
              <span>Held / Total</span>
              <strong style={{ color: 'var(--dhyan-text-main)' }}>
                {result.notifications_held} / {result.notifications_total}
              </strong>
            </div>
          </div>

          {/* Real Notification List */}
          <div style={{ marginTop: '16px' }}>
            <h5 style={{ fontSize: '12px', fontWeight: 800, marginBottom: '8px' }}>
              Real Notifications ({filteredDetails.length} recorded · Click to Inspect Pearl's Abduction Replay)
            </h5>
            <div style={{ maxHeight: '160px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '6px' }}>
              {filteredDetails.slice(0, 12).map((d: any) => (
                <div
                  key={d.notif_id}
                  style={{
                    padding: '8px 12px',
                    background: selectedNotif === d.notif_id ? 'var(--dhyan-mint-bg)' : 'var(--dhyan-card)',
                    borderRadius: '10px',
                    border: '1px solid var(--dhyan-border)',
                    fontSize: '12px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    cursor: 'pointer',
                  }}
                  onClick={() => void loadReplay(d.notif_id)}
                >
                  <span><strong>{d.category}</strong> · Baseline: {d.baseline_action} → CF: {d.cf_action}</span>
                  <span style={{ color: d.time_saved_min > 0 ? 'var(--dhyan-mint-text)' : 'var(--dhyan-text-muted)', fontWeight: 700 }}>
                    {d.time_saved_min > 0 ? `+${d.time_saved_min}m saved` : '0m'}
                  </span>
                </div>
              ))}
            </div>

            {/* Pearl's Abduction Explanation Card */}
            {replay && (
              <div style={{ marginTop: '14px', padding: '14px', background: 'var(--dhyan-card)', borderRadius: '12px', border: '1px solid var(--dhyan-mint-accent)' }}>
                <div style={{ fontSize: '12px', fontWeight: 800, color: 'var(--dhyan-mint-text)' }}>
                  Pearl SCM Abduction Explanation:
                </div>
                <p style={{ fontSize: '12px', margin: '4px 0', color: 'var(--dhyan-text-main)' }}>
                  {replay.explanation}
                </p>
                <div style={{ fontSize: '11px', color: 'var(--dhyan-text-muted)' }}>
                  Abduced Latent Noise U: {replay.latent_noise_abduced} · Conformal 90% Interval: [{replay.counterfactual?.conformal_interval_90?.[0] ?? 0}, {replay.counterfactual?.conformal_interval_90?.[1] ?? 0}] min
                </div>
              </div>
            )}
          </div>
        </div>
      ) : (
        <div className="dhyan-cf-hero" style={{ marginTop: '20px', padding: '16px', textAlign: 'center' }}>
          <h4 style={{ fontSize: '13px', fontWeight: 700, margin: '0 0 6px 0' }}>
            🌱 Warming Up: No Fake Data Pre-Seeded
          </h4>
          <p style={{ fontSize: '12px', color: 'var(--dhyan-text-muted)', margin: 0 }}>
            The Digital Twin starts with zero synthetic events. Real notification arrivals and focus blocks will appear here as they are synced from your phone.
          </p>
        </div>
      )}
    </div>
  );
}
