/**
 * trees.ts — Tree View Providers for the Dhyan sidebar.
 *
 * Implements "Where I Left Off" (dhyan.resume) tree view displaying
 * local resumption snapshot and parked intention.
 */

import type { ResumeManager } from '../features/resume.ts';

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
