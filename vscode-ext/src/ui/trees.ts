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

    // 2. Parked note if present
    if (snapshot.parkedNote) {
      items.push({
        label: `"${snapshot.parkedNote}"`,
        description: 'Parked note',
        tooltip: `Parked note: ${snapshot.parkedNote}`,
        iconPath: this.getThemeIcon('note')
      });
    }

    // 3. Saved timestamp item
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

  getChildren(_element?: TreeItemLike): Thenable<TreeItemLike[]> | Promise<TreeItemLike[]> {
    const items: TreeItemLike[] = [];

    // 1. Current Session Status
    const active = this.sessionManager.getCurrentSession();
    if (active) {
      const elapsedMin = Math.round((Date.now() - active.startTime) / 60_000);
      items.push({
        label: `Focus Session Active: ${elapsedMin}m`,
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

    // 2. Total focus time today
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
