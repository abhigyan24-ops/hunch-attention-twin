import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { PipCompanion } from '../pet/PipCompanion';
import {
  PipBehavior,
  getPipMessage,
  initialPetState,
  PIP_MESSAGES
} from '../pet/petStateMachine';
import './style.css';

declare global {
  interface Window {
    dhyan: {
      schemaVersion: string;
      getStorageHealth():Promise<{paused?:boolean;writePipelineFailures?:number}|null>;
      setIgnoreMouseEvents(ignore: boolean): void;
    };
  }
}

interface HeldItem {
  id: string;
  app: string;
  sender: string;
  reason: string;
  time: string;
}

export function App() {
  const hitbox = useRef<HTMLDivElement>(null);
  const [storagePaused,setStoragePaused]=useState(false);
  useEffect(()=>{let active=true;const poll=()=>{void window.dhyan?.getStorageHealth().then(health=>{if(active)setStoragePaused(!health||health.paused===true||(health.writePipelineFailures??0)>0);}).catch(()=>{if(active)setStoragePaused(true);});};poll();const timer=setInterval(poll,2000);return ()=>{active=false;clearInterval(timer);};},[]);

  // Pip State
  const [behavior, setBehavior] = useState<PipBehavior>('deep_focus');
  const [bubbleText, setBubbleText] = useState<string | null>(
    initialPetState.lastBubbleText
  );
  const [lastBubbleTs, setLastBubbleTs] = useState<number>(Date.now());
  const [isExpanded, setIsExpanded] = useState<boolean>(true);
  const [activeTab, setActiveTab] = useState<'status' | 'held' | 'declare' | 'whatif'>('status');

  // Held Notifications
  const [heldItems, setHeldItems] = useState<HeldItem[]>([
    {
      id: 'h1',
      app: 'Instagram',
      sender: 'sample category',
      reason: '24 min expected lost focus while coding in VS Code',
      time: '4m ago'
    },
    {
      id: 'h2',
      app: 'Slack',
      sender: 'sample category',
      reason: 'Non-urgent channel; batched until next task boundary',
      time: '12m ago'
    }
  ]);

  // Session State
  const [declaredTask, setDeclaredTask] = useState<string>('Coding (VS Code)');
  const [sessionRemainingMin, setSessionRemainingMin] = useState<number>(42);
  const [isStrict, setIsStrict] = useState<boolean>(true);

  // What-If Mini Simulator State
  const [simApp, setSimApp] = useState<'Instagram' | 'Slack' | 'GitHub'>('Instagram');
  const [simShowCost, setSimShowCost] = useState<number>(24);
  const [simSuppressCost, setSimSuppressCost] = useState<number>(2);

  // Windows Click-Through Hit-Testing (Critical Boilerplate per Spec 12.5)
  useEffect(() => {
    const el = hitbox.current;
    if (!el) return;
    const enter = () => window.dhyan?.setIgnoreMouseEvents(false);
    const leave = () => window.dhyan?.setIgnoreMouseEvents(true);
    el.addEventListener('mouseenter', enter);
    el.addEventListener('mouseleave', leave);
    return () => {
      el.removeEventListener('mouseenter', enter);
      el.removeEventListener('mouseleave', leave);
    };
  }, []);

  const handlePipClick = () => {
    // Generate fresh bubble text on tap
    const res = getPipMessage(behavior, 0, Date.now(), true);
    if (res) {
      setBubbleText(res.text);
      setLastBubbleTs(res.updatedTs);
    }
  };

  const handleReleaseHeld = (id: string) => {
    setHeldItems(prev => prev.filter(item => item.id !== id));
    setBehavior('recovering');
    setBubbleText('Released notification. Resuming flow quietly!');
  };

  const handleStartSession = (task: string, min: number) => {
    setDeclaredTask(task);
    setSessionRemainingMin(min);
    setBehavior('deep_focus');
    setBubbleText(`Session declared: ${task} for ${min}m. Pip is guarding your focus.`);
    setActiveTab('status');
  };

  return (
    <div id="pip-hitbox" ref={hitbox} className="dhyan-panel-root">
      {/* Pip Mascot & Top Bar */}
      <div className="pip-topbar">
        <PipCompanion
          behavior={behavior}
          speechBubble={bubbleText}
          heldCount={heldItems.length}
          onClick={handlePipClick}
        />

        <div className="panel-header-info">
          <div className="brand-row">
            <span className="brand-dot" />
            <span className="brand-title">DHYAN</span>
            <span className="brand-hindi">ध्यान</span>
            <span className="schema-tag">v{window.dhyan?.schemaVersion ?? '1.0.0'}</span>
          </div>
          <div className="presence-pill">
            <span className="status-indicator live" />
            <span>VS Code: Flowing</span>
          </div>
        </div>

        <button
          className="collapse-btn"
          onClick={() => setIsExpanded(!isExpanded)}
          title={isExpanded ? 'Collapse panel' : 'Expand panel'}
        >
          {isExpanded ? '▼' : '▲'}
        </button>
      </div>

      {isExpanded && (
        <div className="panel-content">
          {/* Navigation Tabs */}
          <div className="tab-bar">
            <button
              className={`tab-btn ${activeTab === 'status' ? 'active' : ''}`}
              onClick={() => setActiveTab('status')}
            >
              Focus
            </button>
            <button
              className={`tab-btn ${activeTab === 'held' ? 'active' : ''}`}
              onClick={() => setActiveTab('held')}
            >
              Held ({heldItems.length})
            </button>
            <button
              className={`tab-btn ${activeTab === 'declare' ? 'active' : ''}`}
              onClick={() => setActiveTab('declare')}
            >
              Session
            </button>
            <button
              className={`tab-btn ${activeTab === 'whatif' ? 'active' : ''}`}
              onClick={() => setActiveTab('whatif')}
            >
              What-If
            </button>
          </div>

          {/* TAB 1: FOCUS STATUS */}
          {activeTab === 'status' && (
            <div className="tab-pane">
              <p>Scaffold demo — displayed focus, held items and pairing are sample data.</p>
              {storagePaused && <p role="alert">Storage paused. Collection needs recovery; recent events stay in memory.</p>}
              <div className="metric-card">
                <div className="metric-label">TODAY'S FLOW DURATION</div>
                <div className="metric-val">3h 48m</div>
                <div className="metric-sub">Target: 4h 00m (95% reached)</div>
                <div className="progress-track">
                  <div className="progress-fill" style={{ width: '95%' }} />
                </div>
              </div>

              {/* Behavior Quick-Switcher for Demo/Validation */}
              <div className="behavior-selector">
                <span className="section-label">PIP COMPANION STATE:</span>
                <div className="chips-grid">
                  {(
                    [
                      'deep_focus',
                      'held',
                      'drifting',
                      'distracted',
                      'recovering',
                      'fatigued',
                      'second_screen_overlap'
                    ] as PipBehavior[]
                  ).map(b => (
                    <button
                      key={b}
                      className={`chip-btn ${behavior === b ? 'active' : ''}`}
                      onClick={() => {
                        setBehavior(b);
                        const msg = getPipMessage(b, 0, Date.now(), true);
                        if (msg) setBubbleText(msg.text);
                      }}
                    >
                      {b.replace(/_/g, ' ')}
                    </button>
                  ))}
                </div>
              </div>

              <div className="relay-footer">
                <span className="relay-dot" />
                <span>Paired with Phone · Relay DH-7829</span>
              </div>
            </div>
          )}

          {/* TAB 2: HELD NOTIFICATIONS DIGEST */}
          {activeTab === 'held' && (
            <div className="tab-pane">
              <p>Scaffold demo — displayed focus, held items and pairing are sample data.</p>
              {storagePaused && <p role="alert">Storage paused. Collection needs recovery; recent events stay in memory.</p>}
              {heldItems.length === 0 ? (
                <div className="empty-state">
                  <p className="empty-title">Queue Clear</p>
                  <p className="empty-sub">No held notifications at this time.</p>
                </div>
              ) : (
                <div className="held-list">
                  {heldItems.map(item => (
                    <div key={item.id} className="held-card">
                      <div className="held-header">
                        <span className="held-app">{item.app}</span>
                        <span className="held-sender">({item.sender})</span>
                        <span className="held-time">{item.time}</span>
                      </div>
                      <div className="held-reason">{item.reason}</div>
                      <div className="held-actions">
                        <button
                          className="action-btn primary"
                          onClick={() => handleReleaseHeld(item.id)}
                        >
                          Show Now
                        </button>
                        <button
                          className="action-btn secondary"
                          onClick={() => handleReleaseHeld(item.id)}
                        >
                          Dismiss
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* TAB 3: DECLARE SESSION */}
          {activeTab === 'declare' && (
            <div className="tab-pane">
              <p>Scaffold demo — displayed focus, held items and pairing are sample data.</p>
              {storagePaused && <p role="alert">Storage paused. Collection needs recovery; recent events stay in memory.</p>}
              <div className="active-session-box">
                <div className="session-title">{declaredTask}</div>
                <div className="session-remaining">
                  {sessionRemainingMin} min remaining
                </div>
                <div className="session-strict">
                  Mode: {isStrict ? 'Strict (Digest all non-VIP)' : 'Flexible'}
                </div>
              </div>

              <span className="section-label">START NEW SPRINT:</span>
              <div className="sprint-presets">
                <button
                  className="preset-btn"
                  onClick={() => handleStartSession('Coding (VS Code)', 90)}
                >
                  Deep Coding (90m)
                </button>
                <button
                  className="preset-btn"
                  onClick={() => handleStartSession('System Design & Spec', 45)}
                >
                  Architecture (45m)
                </button>
                <button
                  className="preset-btn"
                  onClick={() => handleStartSession('Code Review & PRs', 30)}
                >
                  PR Triage (30m)
                </button>
              </div>
            </div>
          )}

          {/* TAB 4: WHAT-IF SIMULATOR */}
          {activeTab === 'whatif' && (
            <div className="tab-pane">
              <p>Scaffold demo — displayed focus, held items and pairing are sample data.</p>
              {storagePaused && <p role="alert">Storage paused. Collection needs recovery; recent events stay in memory.</p>}
              <span className="section-label">INCOMING TEST NOTIFICATION:</span>
              <div className="chips-grid">
                {(['Instagram', 'Slack', 'GitHub'] as const).map(app => (
                  <button
                    key={app}
                    className={`chip-btn ${simApp === app ? 'active' : ''}`}
                    onClick={() => {
                      setSimApp(app);
                      if (app === 'Instagram') {
                        setSimShowCost(24);
                        setSimSuppressCost(2);
                      } else if (app === 'Slack') {
                        setSimShowCost(12);
                        setSimSuppressCost(3);
                      } else {
                        setSimShowCost(6);
                        setSimSuppressCost(1);
                      }
                    }}
                  >
                    {app}
                  </button>
                ))}
              </div>

              <div className="sim-comparison">
                <div className="sim-row show">
                  <span>If Shown:</span>
                  <strong>{simShowCost} min lost</strong>
                </div>
                <div className="sim-row suppress">
                  <span>If Suppressed:</span>
                  <strong>{simSuppressCost} min lost</strong>
                </div>
              </div>

              <div className="sim-verdict">
                Verdict: <strong>DIGEST</strong> (Focus cost 12x higher if shown)
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

createRoot(document.getElementById('root')!).render(<App />);

