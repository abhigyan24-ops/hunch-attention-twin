import React, { useEffect, useState } from 'react';

export function InsightsTab() {
  const [summary, setSummary] = useState<any>(null);
  const [snapshot, setSnapshot] = useState<any>(null);
  const [heldNotifs, setHeldNotifs] = useState<any[]>([]);
  const [diagnostics, setDiagnostics] = useState<any>(null);
  const [showDiagnostics, setShowDiagnostics] = useState(false);
  const [shieldActiveNotice, setShieldActiveNotice] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchInsights = async () => {
    setLoading(true);
    try {
      const [sumRes, snapRes, heldRes, f, i, s, v, fg, ft, mat, pol] = await Promise.all([
        fetch('http://127.0.0.1:8765/v1/insights/human-summary').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/v1/insights/context-snapshot').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/v1/insights/held-notifications?undelivered_only=true').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/profile/focus').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/profile/interruptions').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/profile/stickiness').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/profile/notif_value').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/profile/fragmentation').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/profile/fatigue').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/twin/maturity').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/policy/cache').then(r => r.json()).catch(() => null),
      ]);

      if (sumRes) setSummary(sumRes);
      if (snapRes?.snapshot) setSnapshot(snapRes.snapshot);
      if (Array.isArray(heldRes?.notifications)) setHeldNotifs(heldRes.notifications);

      setDiagnostics({
        focus: f,
        interruptions: i,
        stickiness: s,
        notifValue: v,
        fragmentation: fg,
        fatigue: ft,
        maturity: mat,
        policy: pol,
      });
    } catch {
      // Offline fallback
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchInsights();
    const interval = setInterval(fetchInsights, 15_000);
    return () => clearInterval(interval);
  }, []);

  const cal = summary?.calibrationStatus;
  const forecast = summary?.distractionForecast;
  const peak = summary?.peakFocusWindow;
  const impact = summary?.todayImpact;

  const activateShield = () => {
    setShieldActiveNotice('Quiet Shield activated for 15 minutes.');
    if (window.dhyan?.petAction) {
      void window.dhyan.petAction('focus25');
    }
    setTimeout(() => setShieldActiveNotice(null), 4000);
  };

  return (
    <div className="dhyan-bento-card">
      <div className="dhyan-editorial-title">Today's Focus & Distraction Forecast</div>
      <div className="dhyan-editorial-subtitle">
        Personalized predictions based on your real activity & circadian rhythm
      </div>

      {loading && !summary && <p style={{ fontSize: '12px', marginTop: '12px' }}>Loading focus forecast...</p>}

      {/* 1. Authentic Calibration Status Banner */}
      <div style={{
        margin: '12px 0 16px 0',
        padding: '10px 14px',
        background: cal?.isCalibrated
          ? 'var(--dhyan-mint-bg, rgba(80, 230, 40, 0.08))'
          : 'rgba(245, 158, 11, 0.08)',
        border: `1px solid ${cal?.isCalibrated ? 'var(--dhyan-mint-accent, #50E628)' : '#F59E0B'}`,
        borderRadius: '10px',
        fontSize: '12px',
        fontWeight: 600,
        color: cal?.isCalibrated ? 'var(--dhyan-text-main)' : '#92400E',
      }}>
        {cal?.isCalibrated
          ? `✨ ${cal.statusLabel} (${cal.realMinutesTracked} mins of real activity recorded)`
          : `🌱 ${cal?.statusLabel ?? 'Learning your natural focus rhythm'}`}
      </div>

      {/* 2. Distraction Forecast & Shield Card */}
      <div className="dhyan-cf-hero" style={{ marginBottom: '16px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '8px' }}>
          <div>
            <h4 style={{ fontSize: '14px', fontWeight: 800, margin: 0 }}>
              🎯 Next Vulnerable Window: {forecast?.nextHighRiskWindow ?? 'Calculating...'}
            </h4>
            <p style={{ fontSize: '13px', margin: '6px 0', color: 'var(--dhyan-text-main)', fontWeight: 500 }}>
              {forecast?.humanHeadline ?? 'Observing your real focus spans to predict distraction walls.'}
            </p>
            <p style={{ fontSize: '11px', color: 'var(--dhyan-text-muted)', margin: 0 }}>
              Typical streak: {forecast?.typicalStreakMinutes ?? 25}m · Primary trigger: {forecast?.primaryTrigger ?? 'Phone pickups'}
            </p>
          </div>
          <button
            onClick={activateShield}
            className="dhyan-small-btn"
            style={{
              background: 'var(--dhyan-mint-accent, #50E628)',
              color: '#000',
              fontWeight: 700,
              padding: '8px 14px',
              borderRadius: '8px',
              border: 'none',
              cursor: 'pointer',
            }}
          >
            🛡️ {forecast?.humanActionLabel ?? 'Turn on Quiet Shield for 15 mins'}
          </button>
        </div>
        {shieldActiveNotice && (
          <div style={{ marginTop: '8px', fontSize: '12px', color: '#059669', fontWeight: 600 }}>
            ✓ {shieldActiveNotice}
          </div>
        )}
      </div>

      {/* 3. Human Metrics Grid */}
      <div className="dhyan-learner-grid">
        {/* Peak Focus Card */}
        <div className="dhyan-learner-card">
          <h4>⚡ Peak Focus Window</h4>
          <p style={{ fontWeight: 600, color: 'var(--dhyan-text-main)', margin: '4px 0' }}>
            {peak?.windowLabel ?? 'Calibrating'}
          </p>
          <p style={{ fontSize: '11px', color: 'var(--dhyan-text-muted)' }}>
            {peak?.humanHeadline ?? 'Discovered from your real deep-work rhythm.'}
          </p>
        </div>

        {/* Today's Preserved Deep Work */}
        <div className="dhyan-learner-card">
          <h4>🧘 Today's Deep Work</h4>
          <p style={{ fontWeight: 600, color: 'var(--dhyan-text-main)', margin: '4px 0' }}>
            {impact?.deepWorkMinutes ?? 0} Minutes Protected
          </p>
          <p style={{ fontSize: '11px', color: 'var(--dhyan-text-muted)' }}>
            {impact?.interruptionsBlocked ?? 0} distracting alerts held for breaks
          </p>
        </div>

        {/* Sleep-Adjusted Focus Sizing */}
        <div className="dhyan-learner-card">
          <h4>🌙 Sleep-Matched Session</h4>
          <p style={{ fontWeight: 600, color: 'var(--dhyan-text-main)', margin: '4px 0' }}>
            {impact?.recommendedSessionMinutes ?? 50}m Focus Blocks
          </p>
          <p style={{ fontSize: '11px', color: 'var(--dhyan-text-muted)' }}>
            {impact?.sessionReason ?? 'Synced with phone Bedtime schedule'}
          </p>
        </div>
      </div>

      {/* 4. Where I Left Off (Interruption Recovery Card) */}
      {snapshot && (
        <div className="dhyan-cf-hero" style={{ marginTop: '16px' }}>
          <h4 style={{ fontSize: '13px', fontWeight: 800, margin: '0 0 6px 0' }}>
            📌 Where You Left Off (1-Click Resume)
          </h4>
          <p style={{ fontSize: '12px', color: 'var(--dhyan-text-main)', margin: '2px 0' }}>
            <strong>{snapshot.file_path ? snapshot.file_path.split(/[\\/]/).pop() : 'Active File'}</strong>
            {snapshot.line_number > 0 ? ` (Line ${snapshot.line_number + 1})` : ''}
            {snapshot.workspace_name ? ` · ${snapshot.workspace_name}` : ''}
          </p>
          {snapshot.user_note && (
            <p style={{ fontSize: '12px', fontStyle: 'italic', color: 'var(--dhyan-text-muted)', margin: '4px 0' }}>
              "{snapshot.user_note}"
            </p>
          )}
          <div style={{ marginTop: '8px' }}>
            <span className="dhyan-badge-pill" style={{ background: '#EEF2FF', color: '#4338CA' }}>
              Snapshot saved automatically upon break
            </span>
          </div>
        </div>
      )}

      {/* 5. Held for You Break Digest */}
      {heldNotifs.length > 0 && (
        <div className="dhyan-bento-card" style={{ marginTop: '16px', background: 'rgba(238, 242, 255, 0.5)' }}>
          <h4 style={{ fontSize: '13px', fontWeight: 800, margin: '0 0 6px 0' }}>
            📬 Held for Your Next Break ({heldNotifs.length} Alerts)
          </h4>
          <p style={{ fontSize: '11px', color: 'var(--dhyan-text-muted)', margin: '0 0 8px 0' }}>
            Raw notification text is never stored. Protected quietly in memory.
          </p>
          <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
            {heldNotifs.map((n, idx) => (
              <span key={idx} className="dhyan-badge-pill">
                {n.app_label} {n.count > 1 ? `(${n.count})` : ''}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* 6. Collapsible Developer Diagnostics Toggle */}
      <div style={{ marginTop: '24px', borderTop: '1px solid #E2E8F0', paddingTop: '12px' }}>
        <button
          onClick={() => setShowDiagnostics(!showDiagnostics)}
          style={{
            background: 'none',
            border: 'none',
            fontSize: '11px',
            color: 'var(--dhyan-text-muted)',
            cursor: 'pointer',
            padding: 0,
            fontWeight: 600,
          }}
        >
          {showDiagnostics ? '▲ Hide Advanced Model Diagnostics' : '▼ Developer & Model Diagnostics (Raw Telemetry)'}
        </button>

        {showDiagnostics && diagnostics && (
          <div style={{ marginTop: '12px' }}>
            <div className="dhyan-learner-grid">
              <div className="dhyan-learner-card">
                <h4>Focus Heatmap Capacity</h4>
                <div className="dhyan-badge-pill">
                  {diagnostics.focus?.real_blocks_count > 0 ? `${diagnostics.focus.real_blocks_count} real blocks` : 'Prior default'}
                </div>
              </div>
              <div className="dhyan-learner-card">
                <h4>Interruption Hazard</h4>
                <div className="dhyan-badge-pill">
                  Excess Break Ratio: {Math.round((diagnostics.interruptions?.excess_break_ratio ?? 0) * 100)}%
                </div>
              </div>
              <div className="dhyan-learner-card">
                <h4>Task Residue Index</h4>
                <div className="dhyan-badge-pill">
                  Switching Penalty: +{diagnostics.fragmentation?.switching_penalty_ms ?? 450}ms
                </div>
              </div>
              <div className="dhyan-learner-card">
                <h4>Nudge Backoff</h4>
                <div className="dhyan-badge-pill">
                  Multiplier: {diagnostics.policy?.thresholds?.threshold_multiplier ?? 1.0}x
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
