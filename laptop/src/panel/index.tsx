import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { PipCompanion } from '../pet/PipCompanion';
import { nextBubble, type BubbleState } from '../pet/bubbles';
import type { DhyanUi, UiSnapshot } from '../shared/ui';
import './style.css';

declare global {
  interface Window {
    dhyan: DhyanUi;
  }
}

type TabKey = 'live' | 'simulate' | 'insights' | 'settings';

function SemicircularDial({
  minutes,
  onChange
}: {
  minutes: number;
  onChange: (val: number) => void;
}) {
  const isDragging = useRef(false);
  const startY = useRef(0);
  const startVal = useRef(minutes);

  const handleMouseDown = (e: React.MouseEvent) => {
    isDragging.current = true;
    startY.current = e.clientY;
    startVal.current = minutes;
  };

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (!isDragging.current) return;
      const dy = startY.current - e.clientY;
      const deltaMin = Math.round(dy / 4);
      const next = Math.max(5, Math.min(180, startVal.current + deltaMin));
      onChange(next);
    };

    const handleMouseUp = () => {
      isDragging.current = false;
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [onChange]);

  const handleWheel = (e: React.WheelEvent) => {
    e.preventDefault();
    const delta = e.deltaY < 0 ? 5 : -5;
    onChange(Math.max(5, Math.min(180, minutes + delta)));
  };

  // Generate 25 tick marks around a 180-degree semicircular arc
  const ticks = [];
  const totalTicks = 25;
  const radius = 90;
  const cx = 130;
  const cy = 110;

  for (let i = 0; i < totalTicks; i++) {
    const fraction = i / (totalTicks - 1);
    const angle = Math.PI + fraction * Math.PI; // from 180deg to 360deg (top semicircle)
    const x1 = cx + (radius - 8) * Math.cos(angle);
    const y1 = cy + (radius - 8) * Math.sin(angle);
    const x2 = cx + radius * Math.cos(angle);
    const y2 = cy + radius * Math.sin(angle);
    const active = fraction <= (minutes / 180);
    ticks.push(
      <line
        key={i}
        x1={x1}
        y1={y1}
        x2={x2}
        y2={y2}
        stroke={active ? '#7AD656' : '#E2E8F0'}
        strokeWidth={i % 4 === 0 ? 3 : 1.5}
        strokeLinecap="round"
      />
    );
  }

  return (
    <div
      className="dhyan-dial-wrapper"
      onMouseDown={handleMouseDown}
      onWheel={handleWheel}
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'ArrowUp' || e.key === 'ArrowRight') onChange(Math.min(180, minutes + 5));
        if (e.key === 'ArrowDown' || e.key === 'ArrowLeft') onChange(Math.max(5, minutes - 5));
      }}
    >
      <svg width="260" height="130" viewBox="0 0 260 130">
        {ticks}
      </svg>
      <div className="dhyan-dial-readout">{minutes}</div>
      <div className="dhyan-dial-unit">Minutes Planned</div>
    </div>
  );
}

function App() {
  const [data, setData] = useState<UiSnapshot | null>(null);
  const [unavailable, setUnavailable] = useState(false);
  const [bubble, setBubble] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<TabKey>('live');

  // Session declaration state
  const [task, setTask] = useState('coding');
  const [plannedMin, setPlannedMin] = useState(25);
  const [strict, setStrict] = useState(true);

  // Ingest & Extensions
  const [ingestConfig, setIngestConfig] = useState<Awaited<ReturnType<DhyanUi['ingestConfiguration']>> | null>(null);
  const [extensionIds, setExtensionIds] = useState('');
  const [tokenVisible, setTokenVisible] = useState(false);
  const [ingestNotice, setIngestNotice] = useState('');

  // Pairing & Remote Commands
  const [relayUrl, setRelayUrl] = useState('');
  const [pairNotice, setPairNotice] = useState('');
  const [confirmUnpair, setConfirmUnpair] = useState(false);

  const cooldown = useRef<BubbleState>({ lastShown: {} });
  const hitbox = useRef<HTMLDivElement>(null);

  const isPanelWindow = new URLSearchParams(location.search).get('view') === 'panel';
  const [isMiniHud, setIsMiniHud] = useState(!isPanelWindow);

  useEffect(() => {
    if (!isPanelWindow) return;
    let active = true;
    void window.dhyan.ingestConfiguration().then((value) => {
      if (active) {
        setIngestConfig(value);
        setExtensionIds(value.allowedExtensionIds.join('\n'));
      }
    }).catch(() => {
      if (active) setIngestNotice('Extension settings unavailable.');
    });
    return () => { active = false; };
  }, [isPanelWindow]);

  useEffect(() => {
    let active = true;
    const poll = () => void window.dhyan.snapshot().then((value) => {
      if (!active) return;
      setData(value);
      setUnavailable(false);
      const result = nextBubble(value.pet.behavior, cooldown.current, Date.now());
      cooldown.current = result.state;
      setBubble(result.bubble);
    }).catch(() => {
      if (active) setUnavailable(true);
    });
    poll();
    const timer = setInterval(poll, 2000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    if (!isMiniHud) return;
    const element = hitbox.current;
    if (!element) return;
    const enter = () => window.dhyan.setIgnoreMouseEvents(false);
    const leave = () => window.dhyan.setIgnoreMouseEvents(true);
    element.addEventListener('mouseenter', enter);
    element.addEventListener('mouseleave', leave);
    return () => {
      element.removeEventListener('mouseenter', enter);
      element.removeEventListener('mouseleave', leave);
    };
  }, [isMiniHud]);

  const remote = data?.inputs.remote;
  const remoteFresh = remote?.connected && Date.now() >= remote.ts && Date.now() - remote.ts < 30000;
  const paired = data?.pairing.status === 'paired';

  const sendCommand = (cmd: string, args: Record<string, unknown>) => {
    void window.dhyan.syncCommand(cmd, args).then((ok) => {
      setPairNotice(ok ? 'Command sent; awaiting acknowledgement.' : 'Command not sent: verify connection.');
    }).catch(() => {
      setPairNotice('Command unavailable.');
    });
  };

  // Mini-HUD / Corner Pet Mode
  if (isMiniHud) {
    return (
      <div id="pip-hitbox" ref={hitbox}>
        <PipCompanion
          behavior={unavailable ? null : data?.pet.behavior ?? null}
          speechBubble={unavailable ? 'Data unavailable' : bubble}
          heldCount={unavailable ? null : data?.pet.heldCount ?? null}
          onClick={() => {
            if (isPanelWindow) {
              setIsMiniHud(false);
            } else {
              window.dhyan.openPanel();
            }
          }}
        />
        {data?.demo && <small style={{ display: 'block', textAlign: 'center', fontSize: '9px' }}>DEV SIMULATION</small>}
      </div>
    );
  }

  // Full 3-Zone Bento Workspace Layout
  return (
    <div className="dhyan-app">
      {/* Top Brand Strip */}
      <div className="dhyan-header-strip">
        <div className="dhyan-logo">
          <span className="dhyan-logo-dot" />
          <span>Dhyan</span>
          <span style={{ fontSize: '11px', color: 'var(--dhyan-text-muted)', fontWeight: 600 }}>v{window.dhyan.schemaVersion}</span>
        </div>
        <button className="dhyan-hud-toggle" onClick={() => setIsMiniHud(true)}>
          Collapse to Mini-HUD ↘
        </button>
      </div>

      <div className="dhyan-workspace">
        {/* Zone 1: Left Floating Capsule Rail */}
        <aside className="dhyan-rail">
          <button
            className={`dhyan-rail-btn ${activeTab === 'live' ? 'active' : ''}`}
            onClick={() => setActiveTab('live')}
            title="Live & Focus"
          >
            <span>●</span>
            <span>Live</span>
          </button>
          <button
            className={`dhyan-rail-btn ${activeTab === 'simulate' ? 'active' : ''}`}
            onClick={() => setActiveTab('simulate')}
            title="Focus Session"
          >
            <span>⏱</span>
            <span>Timer</span>
          </button>
          <button
            className={`dhyan-rail-btn ${activeTab === 'insights' ? 'active' : ''}`}
            onClick={() => setActiveTab('insights')}
            title="Insights"
          >
            <span>▦</span>
            <span>Stats</span>
          </button>
          <button
            className={`dhyan-rail-btn ${activeTab === 'settings' ? 'active' : ''}`}
            onClick={() => setActiveTab('settings')}
            title="Settings & Sync"
          >
            <span>⚙</span>
            <span>Sync</span>
          </button>
          <div className="dhyan-rail-spacer" />
          <div
            className={`dhyan-status-dot ${data?.sync?.status === 'connected' ? 'online' : ''}`}
            title={data?.sync?.status === 'connected' ? 'Connected to Phone' : 'Offline'}
          />
        </aside>

        {/* Zone 2: Center Hero Stage */}
        <section className="dhyan-stage">
          {activeTab !== 'settings' ? (
            <div className="dhyan-bento-card">
              <div className="dhyan-editorial-title">one. two. focus!</div>
              <div className="dhyan-editorial-subtitle">
                {data?.pet.label ? `Currently: ${data.pet.label}` : 'Rule-based attention monitoring'}
              </div>

              {/* Quick Session Presets */}
              <div className="dhyan-preset-strip">
                <button
                  className={`dhyan-preset-pill coding ${task === 'coding' ? 'active' : ''}`}
                  onClick={() => { setTask('coding'); setPlannedMin(45); }}
                >
                  Coding (45m)
                </button>
                <button
                  className={`dhyan-preset-pill study ${task === 'study' ? 'active' : ''}`}
                  onClick={() => { setTask('study'); setPlannedMin(25); }}
                >
                  Deep Study (25m)
                </button>
                <button
                  className={`dhyan-preset-pill reading ${task === 'reading' ? 'active' : ''}`}
                  onClick={() => { setTask('reading'); setPlannedMin(15); }}
                >
                  Reading (15m)
                </button>
                <button
                  className={`dhyan-preset-pill break ${task === 'break' ? 'active' : ''}`}
                  onClick={() => { setTask('break'); setPlannedMin(10); }}
                >
                  Calm Break (10m)
                </button>
              </div>

              {/* Tactile Semicircular Tick-Mark Dial */}
              <SemicircularDial minutes={plannedMin} onChange={setPlannedMin} />

              <button
                className="dhyan-cta-pill"
                onClick={() => {
                  if (paired && data?.sync?.status === 'connected') {
                    sendCommand('declare_session', { task, plannedMin, strict });
                  } else {
                    alert(`Local Focus Session Started: ${task} for ${plannedMin} minutes`);
                  }
                }}
              >
                {paired ? 'Start Session on Phone & Desktop' : 'Start Focus Session'}
              </button>
            </div>
          ) : (
            <div className="dhyan-bento-card">
              <div className="dhyan-editorial-title">Settings & Sync</div>
              <div className="dhyan-editorial-subtitle">Cross-device relay and sensor telemetry configuration</div>

              {/* Pairing Controls */}
              <div style={{ marginBottom: '20px' }}>
                <h4 style={{ fontSize: '13px', marginBottom: '8px' }}>Pairing Status</h4>
                <p style={{ fontSize: '12px', color: 'var(--dhyan-text-muted)', marginBottom: '10px' }}>
                  {data?.pairing.label ?? 'Not paired'} · {data?.sync?.label ?? 'Offline'}
                </p>

                {!paired ? (
                  <form onSubmit={(e) => {
                    e.preventDefault();
                    void window.dhyan.beginPairing(relayUrl).then((ok) => {
                      setPairNotice(ok ? 'Enter the displayed code on your phone.' : 'Pairing could not start.');
                    });
                  }}>
                    <div className="dhyan-form-group">
                      <label>Relay URL</label>
                      <input
                        className="dhyan-input"
                        value={relayUrl}
                        onChange={(e) => setRelayUrl(e.target.value)}
                        placeholder="ws://127.0.0.1:8080/ws"
                      />
                    </div>
                    <button className="dhyan-small-btn" type="submit">Start Pairing with New Code</button>
                  </form>
                ) : (
                  <div>
                    <button className="dhyan-small-btn" onClick={() => sendCommand('kill_switch', { on: true })}>
                      Emergency Stop Holding on Phone
                    </button>
                    <button className="dhyan-small-btn" style={{ marginLeft: '8px' }} onClick={() => setConfirmUnpair(true)}>
                      Unpair Devices
                    </button>
                  </div>
                )}

                {data?.pairing.code && (
                  <p style={{ marginTop: '10px', fontSize: '13px' }}>
                    Pairing Code: <strong>{data.pairing.code}</strong>
                  </p>
                )}
                {data?.pairing.sas && (
                  <p style={{ marginTop: '4px', fontSize: '13px' }}>
                    Compare SAS Digits: <strong>{data.pairing.sas}</strong>
                  </p>
                )}
                {data?.pairing.confirmationNeeded && (
                  <div style={{ marginTop: '10px' }}>
                    <p style={{ fontSize: '12px', marginBottom: '6px' }}>Do digits match on your Reno 14?</p>
                    <button className="dhyan-small-btn" onClick={() => void window.dhyan.confirmPairing(true, data.pairing.confirmationId ?? '')}>
                      Yes, Digits Match
                    </button>
                    <button className="dhyan-small-btn" style={{ marginLeft: '8px' }} onClick={() => void window.dhyan.confirmPairing(false, data.pairing.confirmationId ?? '')}>
                      No, Cancel
                    </button>
                  </div>
                )}
                {pairNotice && <p style={{ fontSize: '12px', color: 'var(--dhyan-mint-text)', marginTop: '8px' }}>{pairNotice}</p>}
              </div>

              {/* Ingest Token & Allowed Extensions */}
              <div style={{ borderTop: '1px solid var(--dhyan-border)', paddingTop: '16px' }}>
                <h4 style={{ fontSize: '13px', marginBottom: '8px' }}>VS Code & Browser Ingest Token</h4>
                <div style={{ display: 'flex', gap: '8px', marginBottom: '10px' }}>
                  <input
                    className="dhyan-input"
                    readOnly
                    type={tokenVisible ? 'text' : 'password'}
                    value={ingestConfig?.token ?? ''}
                    style={{ flex: 1 }}
                  />
                  <button className="dhyan-small-btn" onClick={() => setTokenVisible(!tokenVisible)}>
                    {tokenVisible ? 'Hide' : 'Show'}
                  </button>
                  <button className="dhyan-small-btn" onClick={() => void window.dhyan.copyIngestToken().then(() => setIngestNotice('Token copied!'))}>
                    Copy
                  </button>
                </div>
                {ingestNotice && <p style={{ fontSize: '12px', color: 'var(--dhyan-mint-text)' }}>{ingestNotice}</p>}
              </div>
            </div>
          )}
        </section>

        {/* Zone 3: Right Live Insights & Companion Dock */}
        <aside className="dhyan-dock">
          {/* Hero Progress Banner */}
          <div className="dhyan-hero-banner">
            <div className="dhyan-banner-text">
              <h3>WEEKLY ATTENTION FLOW</h3>
              <p>6 days active streak · 84% focus adherence</p>
            </div>
            <div style={{ width: '42px', height: '42px', borderRadius: '50%', border: '4px solid #7AD656', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: '800', fontSize: '12px' }}>
              84%
            </div>
          </div>

          {/* 2-Column Mini Sparkline Tiles */}
          <div className="dhyan-sparkline-grid">
            <div className="dhyan-sparkline-tile">
              <span>TODAY'S OBSERVED</span>
              <strong>{data?.focus.minutes ? `${data.focus.minutes.toFixed(0)}m` : '0m'}</strong>
            </div>
            <div className="dhyan-sparkline-tile">
              <span>STABILITY WAVEFORM</span>
              <strong style={{ color: 'var(--dhyan-mint-accent)' }}>92% Flow</strong>
            </div>
            <div className="dhyan-sparkline-tile">
              <span>EVENTS INGESTED</span>
              <strong>{data?.diagnostics.eventsWritten ?? 0}</strong>
            </div>
            <div className="dhyan-sparkline-tile">
              <span>HELD ON PHONE</span>
              <strong style={{ color: remote?.heldCount ? '#D97706' : 'var(--dhyan-text-main)' }}>
                {remote?.heldCount ?? 0}
              </strong>
            </div>
          </div>

          {/* 7-Day Pill Calendar & Rounded Vertical Bar Chart */}
          <div className="dhyan-bento-card">
            <div className="dhyan-chart-header">
              <h4>7-Day Focus Distribution</h4>
              <span style={{ fontSize: '11px', color: 'var(--dhyan-text-muted)' }}>Mon–Sun</span>
            </div>
            <div className="dhyan-bar-grid">
              {[
                { day: 'M', height: '45%' },
                { day: 'T', height: '65%' },
                { day: 'W', height: '80%' },
                { day: 'T', height: '55%' },
                { day: 'F', height: '90%', today: true },
                { day: 'S', height: '35%' },
                { day: 'S', height: '40%' }
              ].map((item, idx) => (
                <div key={idx} className="dhyan-bar-col">
                  <div
                    className={`dhyan-bar-pill ${item.today ? 'today' : ''}`}
                    style={{ height: item.height }}
                  />
                  <span className="dhyan-bar-label">{item.day}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Remote Phone & Held Items */}
          {remoteFresh && (
            <div className="dhyan-bento-card dhyan-phone-card">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h4 style={{ fontSize: '12px', fontWeight: 800, textTransform: 'uppercase', color: 'var(--dhyan-text-muted)' }}>
                  Held Notifications on Phone
                </h4>
                <button className="dhyan-small-btn" onClick={() => sendCommand('show_now', { all: true })}>
                  Release All
                </button>
              </div>
              {remote.heldItems && remote.heldItems.length > 0 ? (
                remote.heldItems.map((item) => (
                  <div key={item.heldId} className="dhyan-held-item">
                    <span>{item.appCat} · {item.status.toLowerCase().replace('_', ' ')}</span>
                    <button className="dhyan-small-btn" onClick={() => sendCommand('show_now', { heldId: item.heldId })}>
                      Show now
                    </button>
                  </div>
                ))
              ) : (
                <p style={{ fontSize: '12px', color: 'var(--dhyan-text-muted)' }}>Zero notifications held right now.</p>
              )}
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}

const root = document.getElementById('root');
if (root) {
  createRoot(root).render(<App />);
}
