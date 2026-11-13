/**
 * trees.ts — Tree View Providers for the Dhyan sidebar.
 *
 * Implements:
 *   - "Today" (dhyan.today) focus timeline & metrics
 *   - "Where I Left Off" (dhyan.resume) local resumption snapshot
 *   - "Held for You" (dhyan.digest) notifications held during focus
 */

import type { ResumeManager } from '../features/resume.ts';
import type { SessionManager } from '../features/session.ts';
import type { DigestManager } from '../features/digest.ts';

export interface TreeItemLike {
  label: string;
  description?: string;
  tooltip?: string;
  iconPath?: any;
  command?: {
    command: string;
    title: string;
    arguments?: any[];
  };
  collapsibleState?: number;
}

export class ResumeTreeDataProvider {
  private vscodeApi: any;
  private resumeManager: ResumeManager;
  private _onDidChangeTreeData: any = null;
  readonly onDidChangeTreeData: any = null;

  constructor(vscodeApi: any, resumeManager: ResumeManager) {
    this.vscodeApi = vscodeApi;
    this.resumeManager = resumeManager;

    if (vscodeApi?.EventEmitter) {
      this._onDidChangeTreeData = new vscodeApi.EventEmitter();
      this.onDidChangeTreeData = this._onDidChangeTreeData.event;
    }
  }

  refresh(): void {
    if (this._onDidChangeTreeData) {
      this._onDidChangeTreeData.fire();
    }
  }

  getTreeItem(element: TreeItemLike): TreeItemLike {
    return element;
  }

  getChildren(_element?: TreeItemLike): Thenable<TreeItemLike[]> | Promise<TreeItemLike[]> {
    const snapshot = this.resumeManager.getSnapshot();

    if (!snapshot || !snapshot.filePath) {
      const emptyItem: TreeItemLike = {
        label: 'No interrupted task recorded',
        description: 'Workspace state is clean',
        tooltip: 'When you take a break or park an intention, resume options appear here.',
        iconPath: this.getThemeIcon('info')
      };
      return Promise.resolve([emptyItem]);
    }

    const items: TreeItemLike[] = [];

    // 1. Resume primary action item
    items.push({
      label: `${snapshot.fileName}:${snapshot.line + 1}`,
      description: 'Click to resume',
      tooltip: `Resume editing at line ${snapshot.line + 1} in ${snapshot.fileName}`,
      iconPath: this.getThemeIcon('history'),
      command: {
        command: 'dhyan.resume',
        title: 'Resume Interrupted Task'
      }
    });

    // 2. Enclosing symbol (Mental Replay)
    if (snapshot.enclosingSymbol) {
      items.push({
        label: `Symbol: ${snapshot.enclosingSymbol}`,
        description: 'Context',
        tooltip: `Enclosing function or symbol: ${snapshot.enclosingSymbol}`,
        iconPath: this.getThemeIcon('symbol-method')
      });
    }

    // 3. Git uncommitted summary
    if (snapshot.gitStatSummary) {
      items.push({
        label: `Git: ${snapshot.gitStatSummary}`,
        description: 'Changes',
        tooltip: `Git working tree changes: ${snapshot.gitStatSummary}`,
        iconPath: this.getThemeIcon('git-commit')
      });
    }

    // 4. Last build summary
    if (snapshot.lastBuildSummary) {
      items.push({
        label: `Build: ${snapshot.lastBuildSummary}`,
        description: 'Last outcome',
        tooltip: `Last observed task: ${snapshot.lastBuildSummary}`,
        iconPath: this.getThemeIcon('pass')
      });
    }

    // 5. Parked note if present
    if (snapshot.parkedNote) {
      items.push({
        label: `"${snapshot.parkedNote}"`,
        description: 'Parked note',
        tooltip: `Parked note: ${snapshot.parkedNote}`,
        iconPath: this.getThemeIcon('note')
      });
    }

    // 6. Saved timestamp item
    const savedTime = new Date(snapshot.timestamp).toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit'
    });
    items.push({
      label: `Saved at ${savedTime}`,
      description: snapshot.taskKey !== '0000000000000000' ? `task: ${snapshot.taskKey.slice(0, 8)}` : undefined,
      tooltip: `Snapshot saved at ${new Date(snapshot.timestamp).toISOString()}`,
      iconPath: this.getThemeIcon('clock')
    });

    return Promise.resolve(items);
  }

  private getThemeIcon(id: string): any {
    if (this.vscodeApi?.ThemeIcon) {
      return new this.vscodeApi.ThemeIcon(id);
    }
    return undefined;
  }
}

export class TodayTreeDataProvider {
  private vscodeApi: any;
  private sessionManager: SessionManager;
  private _onDidChangeTreeData: any = null;
  readonly onDidChangeTreeData: any = null;

  constructor(vscodeApi: any, sessionManager: SessionManager) {
    this.vscodeApi = vscodeApi;
    this.sessionManager = sessionManager;

    if (vscodeApi?.EventEmitter) {
      this._onDidChangeTreeData = new vscodeApi.EventEmitter();
      this.onDidChangeTreeData = this._onDidChangeTreeData.event;
    }
  }

  refresh(): void {
    if (this._onDidChangeTreeData) {
      this._onDidChangeTreeData.fire();
    }
  }

  getTreeItem(element: TreeItemLike): TreeItemLike {
    return element;
  }

  async getChildren(_element?: TreeItemLike): Promise<TreeItemLike[]> {
    const items: TreeItemLike[] = [];

    // 1. Current Session Status
    const active = this.sessionManager.getCurrentSession();
    const activeElapsed = active ? Math.round((Date.now() - active.startTime) / 60_000) : 0;
    if (active) {
      items.push({
        label: `Focus Session Active: ${activeElapsed}m`,
        description: 'In flow',
        tooltip: `Active session started ${new Date(active.startTime).toLocaleTimeString()}`,
        iconPath: this.getThemeIcon('eye'),
        command: {
          command: 'dhyan.toggleTimer',
          title: 'End Focus Session'
        }
      });
    } else {
      items.push({
        label: 'No active session',
        description: 'Click to start',
        tooltip: 'Start a focused work session',
        iconPath: this.getThemeIcon('circle-slash'),
        command: {
          command: 'dhyan.toggleTimer',
          title: 'Start Focus Session'
        }
      });
    }

    // 2. Fetch authentic Distraction Forecast & Peak Focus from engine
    try {
      const res = await fetch(`http://127.0.0.1:8765/v1/insights/human-summary?current_streak_minutes=${activeElapsed}`);
      if (res.ok) {
        const summary = (await res.json()) as any;
        const forecast = summary?.distractionForecast;
        const peak = summary?.peakFocusWindow;
        const cal = summary?.calibrationStatus;

        // Pre-distraction warning if approaching distraction point
        if (forecast?.preDistractionShieldTrigger) {
          items.push({
            label: `⚠️ Distraction wall in ~${forecast.minutesUntilLikelyDistraction}m`,
            description: 'Stay in flow!',
            tooltip: `You usually switch apps around minute ${forecast.typicalStreakMinutes}. Stay focused to extend your streak!`,
            iconPath: this.getThemeIcon('shield')
          });
        }

        // Distraction forecast item
        if (forecast?.nextHighRiskWindow && cal?.isCalibrated) {
          items.push({
            label: `Next vulnerable window: ${forecast.nextHighRiskWindow}`,
            description: `Typical streak: ${forecast.typicalStreakMinutes}m`,
            tooltip: `${forecast.humanHeadline} · Primary trigger: ${forecast.primaryTrigger}`,
            iconPath: this.getThemeIcon('pulse')
          });
        } else if (cal && !cal.isCalibrated) {
          items.push({
            label: 'Learning your focus rhythm',
            description: `${cal.realMinutesTracked}/30m`,
            tooltip: cal.statusLabel,
            iconPath: this.getThemeIcon('sync')
          });
        }

        // Peak focus window
        if (peak?.windowLabel && peak.windowLabel !== 'Calibrating') {
          items.push({
            label: `Peak Focus: ${peak.windowLabel}`,
            description: 'Deepest flow',
            tooltip: peak.humanHeadline,
            iconPath: this.getThemeIcon('zap')
          });
        }

        // Dual-Use Study Mode or Code-Along Active indicator
        if (summary?.studyModeActive) {
          items.push({
            label: `📚 Study Mode Active (${summary?.remainingStudyMinutes ?? 60}m)`,
            description: 'YouTube & Docs Allowed',
            tooltip: 'Dual-Use Study Mode is active. Educational videos and documentation are whitelisted.',
            iconPath: this.getThemeIcon('book')
          });
        } else if (summary?.isCodingActiveRecently) {
          items.push({
            label: '💻 Code-Along Active',
            description: 'Tutorials Whitelisted',
            tooltip: 'Recent IDE activity detected. YouTube and technical Reddit pages are treated as study material.',
            iconPath: this.getThemeIcon('code')
          });
        }
      }

      // Check Parked Tabs
      const tabsRes = await fetch('http://127.0.0.1:8765/v1/flow/parked-tabs?unconsumed_only=true').catch(() => null);
      if (tabsRes && tabsRes.ok) {
        const tabsData = (await tabsRes.json()) as any;
        if (tabsData?.tabs?.length > 0) {
          for (const tab of tabsData.tabs) {
            const badge = tab.paused_badge ? ` [${tab.paused_badge}]` : '';
            items.push({
              label: `🌐 ${tab.title || tab.url}${badge}`,
              description: 'Parked for Break',
              tooltip: `Parked tab: ${tab.url}${tab.paused_badge ? ` (${tab.paused_badge})` : ''}`,
              iconPath: this.getThemeIcon('bookmark')
            });
          }
        }
      }

      // Check Thrash Shield
      const thrashRes = await fetch('http://127.0.0.1:8765/v1/flow/thrash-shield').catch(() => null);
      if (thrashRes && thrashRes.ok) {
        const thrashData = (await thrashRes.json()) as any;
        if (thrashData?.active) {
          items.push({
            label: '⚡ Rage-Scroll Shield Active',
            description: `${thrashData.remainingSeconds}s left`,
            tooltip: `High-friction debugging detected: ${thrashData.reason}. Distractions held.`,
            iconPath: this.getThemeIcon('shield')
          });
        }
      }

      // Check Pre-Commit Sanity Audit
      const auditRes = await fetch('http://127.0.0.1:8765/v1/insights/pre-commit-audit').catch(() => null);
      if (auditRes && auditRes.ok) {
        const auditData = (await auditRes.json()) as any;
        if (auditData?.hasHighRisk) {
          items.push({
            label: `⚠️ Sanity Check: ${auditData.flaggedFiles.length} file(s) flagged`,
            description: 'Fatigue warning',
            tooltip: auditData.summary,
            iconPath: this.getThemeIcon('warning'),
            command: {
              command: 'hunch.preCommitSanityCheck',
              title: 'Run Pre-Commit Sanity Check'
            }
          });
        }
      }

      // Check Shared Desk Items
      const deskRes = await fetch('http://127.0.0.1:8765/v1/flow/shared-desk-items?unconsumed_only=true').catch(() => null);
      if (deskRes && deskRes.ok) {
        const deskData = (await deskRes.json()) as any;
        if (deskData?.items?.length > 0) {
          items.push({
            label: `📲 Desk Inbox: ${deskData.items.length} item(s) from phone`,
            description: 'Ready to review',
            tooltip: `First item: ${deskData.items[0].title || deskData.items[0].content}`,
            iconPath: this.getThemeIcon('inbox')
          });
        }
      }
    } catch {
      // Offline fallback
    }

    // 3. Total focus time today
    const history = this.sessionManager.getSessionHistory();
    const totalMs = history.reduce((acc, s) => acc + s.durationMs, 0);
    const totalMin = Math.round(totalMs / 60_000);
    items.push({
      label: `Today's Focus: ${totalMin}m`,
      description: `${history.length} session${history.length !== 1 ? 's' : ''}`,
      tooltip: `Total deep focus time recorded today: ${totalMin} minutes`,
      iconPath: this.getThemeIcon('history')
    });

    // 3. Recent session items
    for (let i = history.length - 1; i >= 0 && i >= history.length - 5; i--) {
      const s = history[i];
      const mins = Math.round(s.durationMs / 60_000);
      const focusStars = s.focusRating > 0 ? ` · Focus ${s.focusRating}/5` : '';
      items.push({
        label: `Session: ${mins}m${focusStars}`,
        description: new Date(s.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        tooltip: `Focus: ${s.focusRating}/5, Energy: ${s.energyRating}/5`,
        iconPath: this.getThemeIcon('check')
      });
    }

    return Promise.resolve(items);
  }

  private getThemeIcon(id: string): any {
    if (this.vscodeApi?.ThemeIcon) {
      return new this.vscodeApi.ThemeIcon(id);
    }
    return undefined;
  }
}

export class DigestTreeDataProvider {
  private vscodeApi: any;
  private digestManager: DigestManager;
  private _onDidChangeTreeData: any = null;
  readonly onDidChangeTreeData: any = null;

  constructor(vscodeApi: any, digestManager: DigestManager) {
    this.vscodeApi = vscodeApi;
    this.digestManager = digestManager;

    if (vscodeApi?.EventEmitter) {
      this._onDidChangeTreeData = new vscodeApi.EventEmitter();
      this.onDidChangeTreeData = this._onDidChangeTreeData.event;
    }
  }

  refresh(): void {
    if (this._onDidChangeTreeData) {
      this._onDidChangeTreeData.fire();
    }
  }

  getTreeItem(element: TreeItemLike): TreeItemLike {
    return element;
  }

  getChildren(_element?: TreeItemLike): Thenable<TreeItemLike[]> | Promise<TreeItemLike[]> {
    const items = this.digestManager.getItems();

    if (items.length === 0) {
      const emptyItem: TreeItemLike = {
        label: 'No held notifications',
        description: 'Focus protected',
        tooltip: 'Non-urgent notifications held during your focus sessions will appear here.',
        iconPath: this.getThemeIcon('shield')
      };
      return Promise.resolve([emptyItem]);
    }

    const treeItems: TreeItemLike[] = items.map(item => ({
      label: item.title || item.category || 'Notification',
      description: item.category ? `[${item.category}]` : undefined,
      tooltip: item.ts ? `Held at ${new Date(item.ts).toLocaleTimeString()}` : 'Held notification',
      iconPath: this.getThemeIcon('bell'),
      command: {
        command: 'dhyan.openDigest',
        title: 'Open Held Items'
      }
    }));

    return Promise.resolve(treeItems);
  }

  private getThemeIcon(id: string): any {
    if (this.vscodeApi?.ThemeIcon) {
      return new this.vscodeApi.ThemeIcon(id);
    }
    return undefined;
  }
}
