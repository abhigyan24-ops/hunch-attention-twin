import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { PipCompanion } from '../pet/PipCompanion';
import { CloudSpiritCanvas, type SpiritEmotion } from '../pet/CloudSpiritCanvas';
import { nextBubble, type BubbleState } from '../pet/bubbles';
import type { DhyanUi, UiSnapshot } from '../shared/ui';
import './style.css';

declare global {
  interface Window {
    dhyan: DhyanUi;
  }
}

type TabKey = 'live' | 'simulate' | 'insights' | 'settings';

interface PresetConfig {
  id: string;
  label: string;
  minutes: number;
  icon: string;
  categoryClass: string;
  description: string;
}

const PRESETS: PresetConfig[] = [
  {
    id: 'coding',
    label: 'Coding',
    minutes: 45,
    icon: '💻',
    categoryClass: 'coding',
    description: 'High distraction protection'
  },
  {
    id: 'study',
    label: 'Deep Study',
    minutes: 25,
    icon: '⚡',
    categoryClass: 'study',
    description: 'Pomodoro flow sprint'
  },
  {
    id: 'reading',
    label: 'Reading',
    minutes: 15,
    icon: '📖',
    categoryClass: 'reading',
    description: 'Paper & docs review'
  },
  {
    id: 'break',
    label: 'Calm Break',
    minutes: 10,
    icon: '🍵',
    categoryClass: 'break',
    description: 'Attention reset & rest'
  }
];

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
  const radius = 95;
  const cx = 140;
  const cy = 115;

  for (let i = 0; i < totalTicks; i++) {
    const fraction = i / (totalTicks - 1);
    const angle = Math.PI + fraction * Math.PI; // from 180deg to 360deg
    const x1 = cx + (radius - 10) * Math.cos(angle);
    const y1 = cy + (radius - 10) * Math.sin(angle);
    const x2 = cx + radius * Math.cos(angle);
    const y2 = cy + radius * Math.sin(angle);
    const active = fraction <= minutes / 180;
    ticks.push(
      <line
        key={i}
        x1={x1}
        y1={y1}
        x2={x2}
        y2={y2}
        stroke={active ? '#7AD656' : '#E2E8F0'}
        strokeWidth={i % 4 === 0 ? 3.5 : 2}
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
      role="slider"
      aria-valuenow={minutes}
      aria-valuemin={5}
      aria-valuemax={180}
      onKeyDown={(e) => {
        if (e.key === 'ArrowUp' || e.key === 'ArrowRight') onChange(Math.min(180, minutes + 5));
        if (e.key === 'ArrowDown' || e.key === 'ArrowLeft') onChange(Math.max(5, minutes - 5));
      }}
    >
      <svg width="280" height="135" viewBox="0 0 280 135">
        <defs>
          <linearGradient id="arcGlow" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="#7AD656" />
            <stop offset="100%" stopColor="#9FE855" />
          </linearGradient>
        </defs>
        {ticks}
      </svg>
      <div className="dhyan-dial-readout">{minutes}</div>
      <div className="dhyan-dial-unit">Minutes Planned</div>
    </div>
  );
}

function MiniSparkline({ points, color = '#7AD656' }: { points: number[]; color?: string }) {
  const max = Math.max(...points, 1);
  const min = Math.min(...points, 0);
  const range = max - min || 1;
  const w = 110;
  const h = 20;

  const pathD = points
    .map((val, idx) => {
      const x = (idx / (points.length - 1)) * w;
      const y = h - ((val - min) / range) * (h - 4) - 2;
      return `${idx === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`;
    })
    .join(' ');

  return (
    <svg className="dhyan-sparkline-svg" viewBox={`0 0 ${w} ${h}`}>
      <path d={pathD} fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
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
    let dragging = false, lastX = 0, lastY = 0;
    const down = (event: PointerEvent) => { if((event.target as Element).closest('.pip-actions'))return; dragging = true; lastX = event.screenX; lastY = event.screenY; element.setPointerCapture(event.pointerId); window.dhyan.dragPetActive(true); };
    const move = (event: PointerEvent) => { if (!dragging) return; window.dhyan.movePet(event.screenX-lastX,event.screenY-lastY);lastX=event.screenX; lastY=event.screenY; };
    const up = () => { dragging = false; window.dhyan.dragPetActive(false); window.dhyan.setIgnoreMouseEvents(true); };
    element.addEventListener('pointerdown',down);
    element.addEventListener('pointermove',move);
    element.addEventListener('pointerup',up);
    const enter = () => window.dhyan.setIgnoreMouseEvents(false);
    const leave = () => window.dhyan.setIgnoreMouseEvents(true);
    element.addEventListener('mouseenter', enter);
    element.addEventListener('mouseleave', leave);
    return () => {
      element.removeEventListener('mouseenter', enter);
      element.removeEventListener('pointerdown',down);
      element.removeEventListener('pointermove',move);
      element.removeEventListener('pointerup',up);
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

  const spiritEmotion: SpiritEmotion = (() => {
    const b = data?.pet.behavior;
    if (b === 'deep_focus') return 'FOCUSED';
    if (b === 'recovering' || b === 'second_screen_overlap') return 'HAPPY';
    if (b === 'distracted') return 'DISTRACTED';
    if (b === 'fatigued' || b === 'drifting') return 'SLEEPY';
    return 'CURIOUS';
  })();

  // Mini-HUD / Corner Pet Mode
  if (isMiniHud) {
    return (
      <div id="pip-hitbox" ref={hitbox}>
        <PipCompanion
          behavior={unavailable ? null : data?.pet.behavior ?? null}
          speechBubble={unavailable ? 'Data unavailable' : data?.reaction?.message ?? bubble}
          heldCount={unavailable ? null : data?.pet.heldCount ?? null}
          onClick={() => {
            if (isPanelWindow) {
              setIsMiniHud(false);
            } else {
              window.dhyan.openPanel();
            }
          }}
        />
        {data?.reaction?.level === 4 && data.reaction.message && <div className="pip-actions"><button onClick={() => void window.dhyan.petAction('back')}>Back to task</button><button onClick={() => void window.dhyan.petAction('break15')}>Break 15 min</button><button onClick={() => void window.dhyan.petAction('dismiss')}>Not distracted</button><button onClick={() => void window.dhyan.petAction('focus25')}>Focus 25 min</button></div>}
        {data?.reaction?.level === 3 && data.reaction.message && <div className="pip-actions"><button onClick={() => void window.dhyan.petAction('back')}>Back to task</button><button onClick={() => void window.dhyan.petAction('break15')}>Break 15 min</button><button onClick={() => void window.dhyan.petAction('dismiss')}>Not distracted</button></div>}
        {data?.demo && <small style={{ display: 'block', textAlign: 'center', fontSize: '9px' }}>DEV SIMULATION</small>}
      </div>
    );
  }

  // Full 3-Zone Bento Workspace Layout
  return (
    <div className="dhyan-app">
      {/* Top Header Strip */}
      <header className="dhyan-header-strip">
        <div className="dhyan-logo">
          <span className="dhyan-logo-dot" />
          <span>Dhyan</span>
          <span style={{ fontSize: '11px', color: 'var(--dhyan-text-muted)', fontWeight: 700 }}>v{window.dhyan.schemaVersion}</span>
        </div>
        <button className="dhyan-hud-toggle" onClick={() => setIsMiniHud(true)}>
          Compact Pet HUD ↘
        </button>
      </header>

      <div className="dhyan-workspace">
        {/* Zone 1: Left Floating Capsule Rail */}
        <nav className="dhyan-rail" aria-label="Main Navigation">
          <button
            className={`dhyan-rail-btn ${activeTab === 'live' ? 'active' : ''}`}
            onClick={() => setActiveTab('live')}
            title="Live Focus & Companion"
          >
            <span style={{ fontSize: '14px' }}>●</span>
            <span>Live</span>
          </button>
          <button
            className={`dhyan-rail-btn ${activeTab === 'simulate' ? 'active' : ''}`}
            onClick={() => setActiveTab('simulate')}
            title="Focus Session Timer"
          >
            <span style={{ fontSize: '14px' }}>⏱</span>
            <span>Timer</span>
          </button>
          <button
            className={`dhyan-rail-btn ${activeTab === 'insights' ? 'active' : ''}`}
            onClick={() => setActiveTab('insights')}
            title="Attention Analytics"
          >
            <span style={{ fontSize: '14px' }}>▦</span>
            <span>Stats</span>
          </button>
          <button
            className={`dhyan-rail-btn ${activeTab === 'settings' ? 'active' : ''}`}
            onClick={() => setActiveTab('settings')}
            title="Device Sync & Settings"
          >
            <span style={{ fontSize: '14px' }}>⚙</span>
            <span>Sync</span>
          </button>
          <div className="dhyan-rail-spacer" />
          <div
            className={`dhyan-status-dot ${data?.sync?.status === 'connected' ? 'online' : ''}`}
            title={data?.sync?.status === 'connected' ? 'Connected to Phone Relay' : 'Relay Offline'}
          />
        </nav>

        {/* Zone 2: Center Hero Stage */}
        <main className="dhyan-stage">
          {activeTab !== 'settings' ? (
            <>
              {/* Interactive Frosted Cloud Spirit Companion Stage Card */}
              <div className="dhyan-bento-card dhyan-spirit-hero-card">
                <div className="dhyan-spirit-info">
                  <div className="dhyan-spirit-mood-pill">
                    <span className="pulse-dot" />
                    <span>{data?.pet.label ?? 'Calm Presence'}</span>
                  </div>
                  <h2 style={{ fontSize: '20px', fontWeight: 800, letterSpacing: '-0.4px', margin: 0 }}>
                    Frosted Cloud Spirit
                  </h2>
                  <p className="dhyan-spirit-quote">
                    {bubble ?? '"Quietly guarding your focus."'}
                  </p>
                  <p style={{ fontSize: '11px', color: 'var(--dhyan-text-muted)', marginTop: '8px' }}>
                    Click or rub to interact with gentle jelly physics
                  </p>
                </div>
                <CloudSpiritCanvas
                  emotion={spiritEmotion}
                  width={190}
                  height={135}
                  interactive={true}
                />
              </div>

              {/* Semicircular Tick Dial & 2x2 Squircle Presets */}
              <div className="dhyan-bento-card">
                <div className="dhyan-editorial-title">one. two. focus!</div>
                <div className="dhyan-editorial-subtitle">
                  {data?.pet.label ? `Current cadence: ${data.pet.label}` : 'Rule-based attention monitoring'}
                </div>

                {/* 2x2 Pastel Squircle Preset Grid */}
                <div className="dhyan-preset-grid">
                  {PRESETS.map((p) => {
                    const isSelected = task === p.id;
                    return (
                      <div
                        key={p.id}
                        className={`dhyan-squircle-tile ${p.categoryClass} ${isSelected ? 'active' : ''}`}
                        onClick={() => {
                          setTask(p.id);
                          setPlannedMin(p.minutes);
                        }}
                      >
                        <div className="dhyan-squircle-header">
                          <span className="dhyan-squircle-title">{p.icon} {p.label}</span>
                          <span className="dhyan-squircle-badge">{p.minutes}m</span>
                        </div>
                        <div className="dhyan-squircle-desc">{p.description}</div>
                      </div>
                    );
                  })}
                </div>

                {/* Tactile Semicircular Tick-Mark Dial */}
                <SemicircularDial minutes={plannedMin} onChange={setPlannedMin} />

                {/* CTA Action Pill */}
                <button
                  className="dhyan-cta-pill"
                  onClick={() => {
                    if (paired && data?.sync?.status === 'connected') {
                      sendCommand('declare_session', { task, plannedMin, strict });
                    } else {
                      alert(`Local Focus Session Activated: ${task} for ${plannedMin} minutes`);
                    }
                  }}
                >
                  {paired ? 'Start Session on Phone & Desktop 🚀' : 'Start Focus Session'}
                </button>
              </div>
            </>
          ) : (
            /* Settings & Synchronization Bento Card */
            <div className="dhyan-bento-card">
              <div className="dhyan-editorial-title">Settings & Sync</div>
              <div className="dhyan-editorial-subtitle">Cryptographic relay and sensor telemetry configuration</div>

              {/* Pairing Controls */}
              <div style={{ marginBottom: '24px' }}>
                <h4 style={{ fontSize: '13px', fontWeight: 800, marginBottom: '8px' }}>P-256 ECDH Pairing Status</h4>
                <p style={{ fontSize: '12px', color: 'var(--dhyan-text-muted)', marginBottom: '12px' }}>
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
                      <label>Encrypted Relay URL</label>
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
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="dhyan-small-btn" onClick={() => sendCommand('kill_switch', { on: true })}>
                      Emergency Stop Holding on Phone
                    </button>
                    <button className="dhyan-small-btn" onClick={() => setConfirmUnpair(true)}>
                      Unpair Devices
                    </button>
                  </div>
                )}

                {data?.pairing.code && (
                  <p style={{ marginTop: '12px', fontSize: '13px' }}>
                    Pairing Code: <strong>{data.pairing.code}</strong>
                  </p>
                )}
                {data?.pairing.sas && (
                  <p style={{ marginTop: '4px', fontSize: '13px' }}>
                    Compare SAS Digits: <strong>{data.pairing.sas}</strong>
                  </p>
                )}
                {data?.pairing.confirmationNeeded && (
                  <div style={{ marginTop: '12px' }}>
                    <p style={{ fontSize: '12px', marginBottom: '8px' }}>Do digits match on your Reno 14?</p>
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
              <div style={{ borderTop: '1px solid var(--dhyan-border)', paddingTop: '18px' }}>
                <h4 style={{ fontSize: '13px', fontWeight: 800, marginBottom: '8px' }}>VS Code & Browser Ingest Token</h4>
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
        </main>

        {/* Zone 3: Right Live Insights & Companion Dock */}
        <aside className="dhyan-dock">
          {/* Hero Weekly Progress Banner */}
          <div className="dhyan-hero-banner">
            <div className="dhyan-banner-text">
              <h3>WEEKLY ATTENTION FLOW</h3>
              <p>6 days active streak · 84% focus adherence</p>
            </div>
            <div style={{ position: 'relative', width: '48px', height: '48px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <svg className="dhyan-ring-svg" width="48" height="48" viewBox="0 0 48 48">
                <circle className="dhyan-ring-bg" cx="24" cy="24" r="20" />
                <circle
                  className="dhyan-ring-progress"
                  cx="24"
                  cy="24"
                  r="20"
                  strokeDasharray="125.6"
                  strokeDashoffset={125.6 * (1 - 0.84)}
                />
              </svg>
              <span style={{ position: 'absolute', fontWeight: 900, fontSize: '11px' }}>84%</span>
            </div>
          </div>

          {/* 2-Column Mini Sparkline Tiles */}
          <div className="dhyan-sparkline-grid">
            <div className="dhyan-sparkline-tile">
              <span>TODAY'S OBSERVED</span>
              <strong>{data?.focus.minutes ? `${data.focus.minutes.toFixed(0)}m` : '0m'}</strong>
              <MiniSparkline points={[10, 25, 40, 35, 60, 50, 80]} color="#7AD656" />
            </div>
            <div className="dhyan-sparkline-tile">
              <span>STABILITY WAVE</span>
              <strong style={{ color: 'var(--dhyan-mint-accent)' }}>92% Flow</strong>
              <MiniSparkline points={[80, 85, 90, 88, 92, 94, 92]} color="#6366F1" />
            </div>
            <div className="dhyan-sparkline-tile">
              <span>EVENTS INGESTED</span>
              <strong>{data?.diagnostics.eventsWritten ?? 0}</strong>
              <span style={{ fontSize: '10px', color: 'var(--dhyan-text-muted)' }}>Local JSONL pipeline</span>
            </div>
            <div className="dhyan-sparkline-tile">
              <span>HELD ON PHONE</span>
              <strong style={{ color: remote?.heldCount ? '#D97706' : 'var(--dhyan-text-main)' }}>
                {remote?.heldCount ?? 0}
              </strong>
              {remote?.heldCount ? (
                <button
                  className="dhyan-small-btn"
                  style={{ alignSelf: 'flex-start', marginTop: '2px' }}
                  onClick={() => sendCommand('show_now', { all: true })}
                >
                  Release All
                </button>
              ) : (
                <span style={{ fontSize: '10px', color: 'var(--dhyan-mint-text)' }}>Queue clean</span>
              )}
            </div>
          </div>

          {/* 7-Day Pill Calendar & Rounded Vertical Bar Chart */}
          <div className="dhyan-bento-card">
            <div className="dhyan-chart-header">
              <h4>7-Day Focus Distribution</h4>
              <span style={{ fontSize: '11px', color: 'var(--dhyan-text-muted)', fontWeight: 600 }}>Mon–Sun</span>
            </div>
            <div className="dhyan-bar-grid">
              {[
                { day: 'M', pct: '45%', height: '45%' },
                { day: 'T', pct: '65%', height: '65%' },
                { day: 'W', pct: '80%', height: '80%' },
                { day: 'T', pct: '55%', height: '55%' },
                { day: 'F', pct: '90%', height: '90%', today: true },
                { day: 'S', pct: '35%', height: '35%' },
                { day: 'S', pct: '40%', height: '40%' }
              ].map((item, idx) => (
                <div key={idx} className="dhyan-bar-col">
                  <span className="dhyan-bar-pct">{item.pct}</span>
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
