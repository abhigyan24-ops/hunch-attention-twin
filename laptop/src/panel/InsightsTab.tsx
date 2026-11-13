import React, { useEffect, useState } from 'react';

export function InsightsTab() {
  const [summary, setSummary] = useState<any>(null);
  const [snapshot, setSnapshot] = useState<any>(null);
  const [heldNotifs, setHeldNotifs] = useState<any[]>([]);
  const [parkedTabs, setParkedTabs] = useState<any[]>([]);
  const [weeklyWrapped, setWeeklyWrapped] = useState<any>(null);
  const [compileShield, setCompileShield] = useState<any>(null);
  const [diagnostics, setDiagnostics] = useState<any>(null);
  const [showDiagnostics, setShowDiagnostics] = useState(false);
  const [shieldActiveNotice, setShieldActiveNotice] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchInsights = async () => {
    setLoading(true);
    try {
      const [sumRes, snapRes, heldRes, tabsRes, wrapRes, shieldRes, f, i, s, v, fg, ft, mat, pol] = await Promise.all([
        fetch('http://127.0.0.1:8765/v1/insights/human-summary').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/v1/insights/context-snapshot').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/v1/insights/held-notifications?undelivered_only=true').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/v1/flow/parked-tabs?unconsumed_only=true').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/v1/insights/weekly-wrapped').then(r => r.json()).catch(() => null),
        fetch('http://127.0.0.1:8765/v1/flow/compile-shield').then(r => r.json()).catch(() => null),
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
      if (Array.isArray(tabsRes?.tabs)) setParkedTabs(tabsRes.tabs);
      if (wrapRes) setWeeklyWrapped(wrapRes);
      if (shieldRes) setCompileShield(shieldRes);

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

  const consumeTab = async (tabId: string, url: string) => {
    try {
      await fetch('http://127.0.0.1:8765/v1/flow/park-tab/consume', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ tab_id: tabId }),
      });
      setParkedTabs(prev => prev.filter(t => t.tab_id !== tabId));
      window.open(url, '_blank');
    } catch {
      window.open(url, '_blank');
    }
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
              {compileShield?.total_saves ? ` · ${compileShield.total_saves} build interruptions shielded` : ''}
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

      {/* 4. Where I Left Off (10-Second Mental Replay Card) */}
      {snapshot && (
        <div className="dhyan-cf-hero" style={{ marginTop: '16px' }}>
          <h4 style={{ fontSize: '13px', fontWeight: 800, margin: '0 0 6px 0' }}>
            📌 10-Second Mental Replay (Where You Left Off)
          </h4>
          <p style={{ fontSize: '12px', color: 'var(--dhyan-text-main)', margin: '2px 0' }}>
            <strong>{snapshot.file_path ? snapshot.file_path.split(/[\\/]/).pop() : 'Active File'}</strong>
            {snapshot.line_number > 0 ? ` (Line ${snapshot.line_number})` : ''}
            {snapshot.workspace_name ? ` · ${snapshot.workspace_name}` : ''}
          </p>

          {/* Enriched Context Indicators */}
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', margin: '6px 0' }}>
            {snapshot.enclosing_symbol && (
              <span className="dhyan-badge-pill" style={{ background: '#E0E7FF', color: '#3730A3', fontWeight: 600 }}>
                ƒ {snapshot.enclosing_symbol}
              </span>
            )}
            {snapshot.git_stat_summary && (
              <span className="dhyan-badge-pill" style={{ background: '#F1F5F9', color: '#334155' }}>
                ± {snapshot.git_stat_summary}
              </span>
            )}
            {snapshot.last_build_summary && (
              <span className="dhyan-badge-pill" style={{ background: '#ECFDF5', color: '#065F46' }}>
                ✓ {snapshot.last_build_summary}
              </span>
            )}
          </div>

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

      {/* 5. Sunday Attention Wrapped (Upgrade 6) */}
      {weeklyWrapped && (
        <div style={{
          marginTop: '16px',
          padding: '16px 18px',
          background: 'linear-gradient(135deg, #1E1B4B 0%, #312E81 100%)',
          borderRadius: '12px',
          color: '#F8FAFC',
          boxShadow: '0 8px 24px -4px rgba(49, 46, 129, 0.35)',
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h4 style={{ fontSize: '14px', fontWeight: 800, margin: 0, color: '#C7D2FE' }}>
              ✨ Weekly Attention Wrapped
            </h4>
            <span style={{ fontSize: '11px', background: 'rgba(255, 255, 255, 0.15)', padding: '2px 8px', borderRadius: '9999px' }}>
              Past 7 Days
            </span>
          </div>
          <p style={{ fontSize: '13px', margin: '6px 0 12px 0', fontWeight: 600 }}>
            {weeklyWrapped.humanSummaryHeadline}
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(110px, 1fr))', gap: '10px' }}>
            <div style={{ background: 'rgba(255, 255, 255, 0.1)', padding: '8px 12px', borderRadius: '8px' }}>
              <div style={{ fontSize: '11px', color: '#A5B4FC' }}>Deep Work</div>
              <div style={{ fontSize: '16px', fontWeight: 800 }}>{weeklyWrapped.totalDeepWorkHours}h</div>
            </div>
            <div style={{ background: 'rgba(255, 255, 255, 0.1)', padding: '8px 12px', borderRadius: '8px' }}>
              <div style={{ fontSize: '11px', color: '#A5B4FC' }}>Peak Streak</div>
              <div style={{ fontSize: '16px', fontWeight: 800 }}>{weeklyWrapped.longestStreakMinutes}m</div>
            </div>
            <div style={{ background: 'rgba(255, 255, 255, 0.1)', padding: '8px 12px', borderRadius: '8px' }}>
              <div style={{ fontSize: '11px', color: '#A5B4FC' }}>Shield Saves</div>
              <div style={{ fontSize: '16px', fontWeight: 800 }}>{weeklyWrapped.compileShieldSaves}</div>
            </div>
            <div style={{ background: 'rgba(255, 255, 255, 0.1)', padding: '8px 12px', borderRadius: '8px' }}>
              <div style={{ fontSize: '11px', color: '#A5B4FC' }}>Tabs Parked</div>
              <div style={{ fontSize: '16px', fontWeight: 800 }}>{weeklyWrapped.tabsParkedCount}</div>
            </div>
          </div>

          <div style={{ marginTop: '12px', fontSize: '11px', color: '#E0E7FF' }}>
            💡 {weeklyWrapped.sleepFocusInsight}
          </div>
        </div>
      )}

      {/* 6. Held for You Break Digest & Parked Tabs */}
      {(heldNotifs.length > 0 || parkedTabs.length > 0) && (
        <div className="dhyan-bento-card" style={{ marginTop: '16px', background: 'rgba(238, 242, 255, 0.5)' }}>
          <h4 style={{ fontSize: '13px', fontWeight: 800, margin: '0 0 6px 0' }}>
            📬 Held for Your Break ({heldNotifs.length} Alerts · {parkedTabs.length} Tabs)
          </h4>
          <p style={{ fontSize: '11px', color: 'var(--dhyan-text-muted)', margin: '0 0 8px 0' }}>
            Stored locally. Alerts are delivered in a calm batch when your focus block completes.
          </p>

          {heldNotifs.length > 0 && (
            <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginBottom: '8px' }}>
              {heldNotifs.map((n, idx) => (
                <span key={idx} className="dhyan-badge-pill">
                  🔔 {n.app_label} {n.count > 1 ? `(${n.count})` : ''}
                </span>
              ))}
            </div>
          )}

          {parkedTabs.length > 0 && (
            <div style={{ marginTop: '6px' }}>
              <div style={{ fontSize: '12px', fontWeight: 700, margin: '6px 0 4px 0' }}>Parked Tabs:</div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                {parkedTabs.map(t => (
                  <div
                    key={t.tab_id}
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      background: '#fff',
                      padding: '6px 10px',
                      borderRadius: '6px',
                      fontSize: '11px',
                      border: '1px solid #E2E8F0',
                    }}
                  >
                    <span style={{ textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap', maxWidth: '300px' }}>
                      🌐 {t.title || t.url}
                    </span>
                    <button
                      onClick={() => consumeTab(t.tab_id, t.url)}
                      style={{
                        background: '#EEF2FF',
                        color: '#4F46E5',
                        border: '1px solid #C7D2FE',
                        borderRadius: '4px',
                        padding: '2px 8px',
                        cursor: 'pointer',
                        fontSize: '10px',
                        fontWeight: 600,
                      }}
                    >
                      Open & Clear
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* 7. Collapsible Developer Diagnostics Toggle */}
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
